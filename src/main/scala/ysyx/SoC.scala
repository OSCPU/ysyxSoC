package ysyx

import chisel3._
import chisel3.util._

import freechips.rocketchip.diplomacy._
import freechips.rocketchip.config.{Field, Parameters}
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

  val luart = LazyModule(new APBUart16550(AddressSet.misaligned(0x10000000, 0x1000)))
  val lspi  = LazyModule(new APBSPI(
    AddressSet.misaligned(0x10001000, 0x1000) ++    // SPI controller
    AddressSet.misaligned(0x30000000, 0x10000000)   // XIP flash
  ))

  List(lspi.node, luart.node).map(_ := apbxbar)
  List(chiplinkNode, apbxbar := AXI4ToAPB()).map(_ := xbar)
  xbar := cpu.masterNode

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

    // expose chiplink fpga I/O interface as ports
    val fpga_io = IO(chiselTypeOf(chipMaster.module.fpga_io))
    fpga_io <> chipMaster.module.fpga_io

    // expose spi and uart slave interface as ports
    val spi = IO(chiselTypeOf(lspi.module.spi_bundle))
    val uart = IO(chiselTypeOf(luart.module.uart))
    uart <> luart.module.uart
    spi <> lspi.module.spi_bundle
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

    masic.intr_from_chipSlave := false.B

    val spiFlash = Module(new spiFlash)
    spiFlash.io <> masic.spi
    masic.uart.rx := false.B
  }
}
