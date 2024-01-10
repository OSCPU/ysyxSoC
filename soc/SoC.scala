package ysyx

import chisel3._
import chisel3.util._

import freechips.rocketchip.diplomacy._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.subsystem._
import freechips.rocketchip.util._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.amba.apb._
import freechips.rocketchip.system.SimAXIMem

object AXI4SlaveNodeGenerator {
  def apply(params: Option[MasterPortParams], address: Seq[AddressSet])(implicit valName: ValName) =
    AXI4SlaveNode(params.map(p => AXI4SlavePortParameters(
        slaves = Seq(AXI4SlaveParameters(
          address       = address,
          executable    = p.executable,
          supportsWrite = TransferSizes(1, p.maxXferBytes),
          supportsRead  = TransferSizes(1, p.maxXferBytes))),
        beatBytes = p.beatBytes
      )).toSeq)
}

class ysyxSoCASIC(hasChipLink: Boolean)(implicit p: Parameters) extends LazyModule {
  val xbar = AXI4Xbar()
  val apbxbar = LazyModule(new APBFanout).node
  val cpu = LazyModule(new CPU(idBits = ChipLinkParam.idBits))
  val chipMaster = if (hasChipLink) Some(LazyModule(new ChipLinkMaster)) else None
  val chiplinkNode = if (hasChipLink) Some(AXI4SlaveNodeGenerator(p(ExtBus), ChipLinkParam.allSpace)) else None

  val luart = LazyModule(new APBUart16550(AddressSet.misaligned(0x10000000, 0x1000)))
  val lgpio = LazyModule(new APBGPIO(AddressSet.misaligned(0x10002000, 0x10)))
  val lkeyboard = LazyModule(new APBKeyboard(AddressSet.misaligned(0x10011000, 0x8)))
  val lvga = LazyModule(new APBVGA(AddressSet.misaligned(0x21000000, 0x200000)))
  val lspi  = LazyModule(new APBSPI(
    AddressSet.misaligned(0x10001000, 0x1000) ++    // SPI controller
    AddressSet.misaligned(0x30000000, 0x10000000)   // XIP flash
  ))
  val lpsram = LazyModule(new APBPSRAM(AddressSet.misaligned(0x80000000L, 0x400000)))
  val lmrom = LazyModule(new AXI4MROM(AddressSet.misaligned(0x20000000, 0x1000)))
  val sramNode = AXI4RAM(AddressSet.misaligned(0x0f000000, 0x2000).head, false, true, 8, None, Nil, false)
  val lsdram = LazyModule(new APBSDRAM(AddressSet.misaligned(0xa0000000L, 0x2000000)))

  List(lspi.node, luart.node, lpsram.node, lsdram.node, lgpio.node, lkeyboard.node, lvga.node).map(_ := apbxbar)
  List(apbxbar := AXI4ToAPB(), lmrom.node, sramNode).map(_ := xbar)
  if (hasChipLink) chiplinkNode.get := xbar
  xbar := cpu.masterNode

  override lazy val module = new Impl
  class Impl extends LazyModuleImp(this) with DontTouch {
    // generate delayed reset for cpu, since chiplink should finish reset
    // to initialize some async modules before accept any requests from cpu
    //val cpu_reset = IO(Flipped(chiselTypeOf(reset)))
    cpu.module.reset := SynchronizerShiftReg(reset.asBool, 10) || reset.asBool

    val fpga_io = if (hasChipLink) Some(IO(chiselTypeOf(chipMaster.get.module.fpga_io))) else None
    if (hasChipLink) {
      // connect chiplink slave interface to crossbar
      (chipMaster.get.slave zip chiplinkNode.get.in) foreach { case (io, (bundle, _)) => io <> bundle }

      // connect chiplink dma interface to cpu
      cpu.module.slave <> chipMaster.get.master_mem(0)

      // expose chiplink fpga I/O interface as ports
      fpga_io.get <> chipMaster.get.module.fpga_io
    } else {
      cpu.module.slave := DontCare
    }

    // connect interrupt signal to cpu
    val intr_from_chipSlave = IO(Input(Bool()))
    cpu.module.interrupt := intr_from_chipSlave

    // expose slave I/O interface as ports
    val spi = IO(chiselTypeOf(lspi.module.spi_bundle))
    val uart = IO(chiselTypeOf(luart.module.uart))
    val psram = IO(chiselTypeOf(lpsram.module.qspi_bundle))
    val sdram = IO(chiselTypeOf(lsdram.module.sdram_bundle))
    val gpio = IO(chiselTypeOf(lgpio.module.gpio_bundle))
    val ps2 = IO(chiselTypeOf(lkeyboard.module.ps2_bundle))
    val vga = IO(chiselTypeOf(lvga.module.vga_bundle))
    uart <> luart.module.uart
    spi <> lspi.module.spi_bundle
    psram <> lpsram.module.qspi_bundle
    sdram <> lsdram.module.sdram_bundle
    gpio <> lgpio.module.gpio_bundle
    ps2 <> lkeyboard.module.ps2_bundle
    vga <> lvga.module.vga_bundle
  }
}

class ysyxSoCFPGA(implicit p: Parameters) extends ChipLinkSlave


class ysyxSoCFull(implicit p: Parameters) extends LazyModule {
  val hasChipLink = false //true

  val asic = LazyModule(new ysyxSoCASIC(hasChipLink))
  ElaborationArtefacts.add("graphml", graphML)

  override lazy val module = new Impl
  class Impl extends LazyModuleImp(this) with DontTouch {
    val masic = asic.module

    if (hasChipLink) {
      val fpga = LazyModule(new ysyxSoCFPGA)
      val mfpga = Module(fpga.module)
      masic.dontTouchPorts()

      masic.fpga_io.get.b2c <> mfpga.fpga_io.c2b
      mfpga.fpga_io.b2c <> masic.fpga_io.get.c2b

      (fpga.master_mem zip fpga.axi4MasterMemNode.in).map { case (io, (_, edge)) =>
        val mem = LazyModule(new SimAXIMem(edge,
          base = ChipLinkParam.mem.base, size = ChipLinkParam.mem.mask + 1))
        Module(mem.module)
        mem.io_axi4.head <> io
      }

      fpga.master_mmio.map(_ := DontCare)
      fpga.slave.map(_ := DontCare)
    }

    masic.intr_from_chipSlave := false.B

    val flash = Module(new flash)
    flash.io <> masic.spi
    flash.io.ss := masic.spi.ss(0)
    val bitrev = Module(new bitrev)
    bitrev.io <> masic.spi
    bitrev.io.ss := masic.spi.ss(7)
    masic.spi.miso := List(bitrev.io, flash.io).map(_.miso).reduce(_&&_)

    val psram = Module(new psram)
    psram.io <> masic.psram
    val sdram = Module(new sdram)
    sdram.io <> masic.sdram

    val externalPins = IO(new Bundle{
      val gpio = chiselTypeOf(masic.gpio)
      val ps2 = chiselTypeOf(masic.ps2)
      val vga = chiselTypeOf(masic.vga)
      val uart = chiselTypeOf(masic.uart)
    })
    externalPins.gpio <> masic.gpio
    externalPins.ps2 <> masic.ps2
    externalPins.vga <> masic.vga
    externalPins.uart <> masic.uart
  }
}
