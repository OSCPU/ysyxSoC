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
  def apply(params: Option[MasterPortParams], base: BigInt, size: BigInt)(implicit valName: ValName) =
    AXI4SlaveNode(params.map(p => AXI4SlavePortParameters(
        slaves = Seq(AXI4SlaveParameters(
          address       = AddressSet.misaligned(base, size),
          executable    = p.executable,
          supportsWrite = TransferSizes(1, p.maxXferBytes),
          supportsRead  = TransferSizes(1, p.maxXferBytes))),
        beatBytes = p.beatBytes
      )).toSeq)
}

object APBSlaveNodeGenerator {
  def apply(params: Option[MasterPortParams], base: BigInt, size: BigInt)(implicit valName: ValName) =
    APBSlaveNode(params.map(p => APBSlavePortParameters(
      slaves = Seq(APBSlaveParameters(
        address    = AddressSet.misaligned(base, size),
        executable = p.executable
      )),
      beatBytes = 4
    )).toSeq)
}

// split cpu mem and mmio
class ysyxSoCASIC(implicit p: Parameters) extends LazyModule {
  val idBits = 4

  val chipMaster = LazyModule(new ChipLinkMaster)
  val xbar = AXI4Xbar()

  val cpuMasterNode = AXI4MasterNode(p(ExtIn).map(params =>
    AXI4MasterPortParameters(
      masters = Seq(AXI4MasterParameters(
        name = "cpu",
        id   = IdRange(0, 1 << idBits))))).toSeq)

  val chiplinkNode = AXI4SlaveNodeGenerator(p(ExtBus),
      ChipLinkParam.mmio.base, ChipLinkParam.mmio.mask + 1 + ChipLinkParam.mem.mask + 1)

  val spiNode  = APBSlaveNodeGenerator(p(ExtBus), 0x10000000, 0x10001000)
  val uartNode = APBSlaveNodeGenerator(p(ExtBus), 0x20001000, 0x1000)

  List(chiplinkNode, spiNode := AXI4ToAPB(), uartNode := AXI4ToAPB()).map(_ := xbar)
  xbar := cpuMasterNode

  override lazy val module = new Impl
  class Impl extends LazyModuleImp(this) with DontTouch {
    // generate delayed reset for cpu, since chiplink should finish reset
    // to initialize some async modules before accept any requests from cpu
    val cpu_reset = IO(Flipped(chiselTypeOf(reset)))
    cpu_reset := SynchronizerShiftReg(reset.asBool, 10)

    // expose cpu master interface as ports
    val cpu_master  = IO(Flipped(HeterogeneousBag.fromNode(cpuMasterNode.out)))
    (cpuMasterNode.out  zip cpu_master ) foreach { case ((bundle, _), io) => bundle <> io }

    // expose chiplink fpga I/O interface as ports
    val fpga_io = IO(chiselTypeOf(chipMaster.module.fpga_io))
    fpga_io <> chipMaster.module.fpga_io

    // connect chiplink slave interface to crossbar
    (chipMaster.slave zip chiplinkNode.in) foreach { case (io, (bundle, _)) => io <> bundle }

    // expose chiplink dma interface as ports
    val chiplink_dma = chipMaster.master_mem(0)
    val cpu_slave = IO(chiselTypeOf(chiplink_dma))
    cpu_slave <> chiplink_dma

    // expose spi and uart slave interface as ports
    val spi = IO(HeterogeneousBag.fromNode(spiNode.in))
    val uart = IO(HeterogeneousBag.fromNode(uartNode.in))
    List((spi, spiNode), (uart, uartNode)).map { case (io, node) =>
      (io zip node.in) foreach { case (io, (bundle, _)) => io <> bundle }
    }
  }
}

class ysyxSoCFPGA(implicit p: Parameters) extends ChipLinkSlave


class ysyxSoCFull(implicit p: Parameters) extends LazyModule {
  val asic = LazyModule(new ysyxSoCASIC)

  override lazy val module = new Impl
  class Impl extends LazyModuleImp(this) with DontTouch {
    val fpga = LazyModule(new ysyxSoCFPGA)
    val mfpga = Module(fpga.module)
    val masic = asic.module

    masic.fpga_io.b2c <> mfpga.fpga_io.c2b
    mfpga.fpga_io.b2c <> masic.fpga_io.c2b

    (fpga.master_mem zip fpga.axi4MasterMemNode.in).map { case (io, (_, edge)) =>
      val mem = LazyModule(new SimAXIMem(edge,
        base = ChipLinkParam.mem.base, size = ChipLinkParam.mem.mask + 1))
      Module(mem.module)
      mem.io_axi4.head <> io
    }

    fpga.master_mmio.map(_ := DontCare)
    fpga.slave.map(_ := DontCare)

    val cpu_reset  = IO(chiselTypeOf(masic.cpu_reset))
    val cpu_master = IO(chiselTypeOf(masic.cpu_master))
    val cpu_slave  = IO(chiselTypeOf(masic.cpu_slave))
    cpu_reset := masic.cpu_reset
    masic.cpu_master <> cpu_master
    cpu_slave <> masic.cpu_slave

    val spi  = IO(chiselTypeOf(masic.spi))
    val uart = IO(chiselTypeOf(masic.uart))
    spi  <> masic.spi
    uart <> masic.uart
  }
}
