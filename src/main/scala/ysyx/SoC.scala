package ysyx

import chisel3._

import freechips.rocketchip.diplomacy._
import freechips.rocketchip.config.Parameters
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

class ysyxSoCASIC(implicit p: Parameters) extends LazyModule {
  val chipMaster = LazyModule(new ChipLinkMaster)
  val xbar = AXI4Xbar()
  val apbxbar = LazyModule(new APBFanout).node
  val cpu = LazyModule(new CPU(idBits = ChipLinkParam.idBits))
  val chiplinkNode = AXI4SlaveNodeGenerator(p(ExtBus), ChipLinkParam.allSpace)

  // ram
  val sram0 = LazyModule(new RAM())
  val sram1 = LazyModule(new RAM())
  val sram2 = LazyModule(new RAM())
  val sram3 = LazyModule(new RAM())
  val sram4 = LazyModule(new RAM())
  val sram5 = LazyModule(new RAM())
  val sram6 = LazyModule(new RAM())
  val sram7 = LazyModule(new RAM())


  // peripheral
  val luart = LazyModule(new APBUart16550(AddressSet.misaligned(0x10000000, 0x1000)))
  val lspi  = LazyModule(new APBSPI(
    AddressSet.misaligned(0x10001000, 0x1000) ++    // SPI controller
    AddressSet.misaligned(0x30000000, 0x10000000)   // XIP flash
  ))

  val lps2   = LazyModule(new AXI4PS2(AddressSet.misaligned(0x10003000, 0x1000)))
  val hvga   = LazyModule(new AXI4VGA(idBits = ChipLinkParam.idBits,
  AddressSet.misaligned(0x10002000, 0x1000) ++    // slave config
  AddressSet.misaligned(0x1c000000, 0x13FFFFF0)   // frame buffer
  ))
  // val hsdram = LazyModule(new AXI4SDRAM(AddressSet.misaligned(0xFC000000L, 0x3FFFFF0)))

  List(lspi.node, luart.node).map(_ := apbxbar)
  List(chiplinkNode, apbxbar := AXI4ToAPB()).map(_ := xbar)
  List(lps2.node).map(_ := xbar)
  List(hvga.node).map(_ := xbar)
  // List(hsdram.node).map(_ := xbar)
  xbar := cpu.masterNode
  // xbar := hvga.masterNode

  override lazy val module = new LazyModuleImp(this) with DontTouch {
    // generate delayed reset for cpu, since chiplink should finish reset
    // to initialize some async modules before accept any requests from cpu
    //val cpu_reset = IO(Flipped(chiselTypeOf(reset)))
    cpu.module.reset := SynchronizerShiftReg(reset.asBool, 10) || reset.asBool

    // connect chiplink slave interface to crossbar
    (chipMaster.slave zip chiplinkNode.in) foreach { case (io, (bundle, _)) => io <> bundle }

    // connect chiplink dma interface to cpu
    cpu.module.slave <> chipMaster.master_mem

    // connect interrupt signal to cpu
    val intr_from_chipSlave = IO(Input(Bool()))
    cpu.module.interrupt := intr_from_chipSlave

    // connet sram signal to cpu
    cpu.module.sram0 <> sram0.module.io
    cpu.module.sram1 <> sram1.module.io
    cpu.module.sram2 <> sram2.module.io
    cpu.module.sram3 <> sram3.module.io
    cpu.module.sram4 <> sram4.module.io
    cpu.module.sram5 <> sram5.module.io
    cpu.module.sram6 <> sram6.module.io
    cpu.module.sram7 <> sram7.module.io

    // expose chiplink fpga I/O interface as ports
    val fpga_io = IO(chiselTypeOf(chipMaster.module.fpga_io))
    fpga_io <> chipMaster.module.fpga_io

    // expose spi and uart slave interface as ports
    val spi = IO(chiselTypeOf(lspi.module.spi_bundle))
    val uart = IO(chiselTypeOf(luart.module.uart))
    uart <> luart.module.uart
    spi <> lspi.module.spi_bundle

    //expose ps2, vga and sdram slave interface as ports
    val ps2 = IO(chiselTypeOf(lps2.module.io))
    val vga = IO(chiselTypeOf(hvga.module.io))
    // val sdram = IO(chiselTypeOf(hsdram.module.io))
    ps2 <> lps2.module.io
    vga <> hvga.module.io
    // sdram <> hsdram.module.io
  }
}

class ysyxSoCFPGA(implicit p: Parameters) extends ChipLinkSlave


class ysyxSoCFull(implicit p: Parameters) extends LazyModule {
  val asic = LazyModule(new ysyxSoCASIC)
  ElaborationArtefacts.add("graphml", graphML)

  override lazy val module = new LazyModuleImp(this) with DontTouch {
    val fpga = LazyModule(new ysyxSoCFPGA)
    val mfpga = Module(fpga.module)
    val masic = asic.module
    masic.dontTouchPorts()

    masic.fpga_io.b2c <> mfpga.fpga_io.c2b
    mfpga.fpga_io.b2c <> masic.fpga_io.c2b

    (fpga.master_mem zip fpga.axi4MasterMemNode.in).map { case (io, (_, edge)) =>
      val mem = LazyModule(new SimAXIMem(edge,
        base = ChipLinkParam.mem.base, size = ChipLinkParam.mem.mask + 1))
      Module(mem.module)
      mem.io_axi4.head <> io
    }

    fpga.master_mmio.map(_.tieoff())
    fpga.slave.map(_.tieoff())

    // dont test extern intr
    masic.intr_from_chipSlave := false.B

    // spi flash
    val spiFlash = Module(new spiFlash)
    spiFlash.io <> masic.spi
    masic.uart.rx := false.B

    // keyboard
    val kdb = Module(new kdb)
    kdb.io.ps2 <> masic.ps2
    kdb.io.clock := clock
    kdb.io.resetn := ~reset.asBool

    // screen
    val screen = Module(new screen)
    screen.io.dat <> masic.vga;
    screen.io.clock := clock

    // sdram
    // val sdr = Module(new sdr_top)
    // sdr.io <> masic.sdram;
  }
}
