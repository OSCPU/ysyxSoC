package ysyx

import chisel3._
import chisel3.util._

import freechips.rocketchip.diplomacy._
import freechips.rocketchip.config.{Field, Parameters}
import freechips.rocketchip.subsystem._
import freechips.rocketchip.util._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.system.SimAXIMem

object AXI4SlavePortParametersGenerator {
  def apply(params: MasterPortParams, base: BigInt, size: BigInt) =
    AXI4SlavePortParameters(
      slaves = Seq(AXI4SlaveParameters(
        address       = AddressSet.misaligned(base, size),
        executable    = params.executable,
        supportsWrite = TransferSizes(1, params.maxXferBytes),
        supportsRead  = TransferSizes(1, params.maxXferBytes))),
      beatBytes = params.beatBytes)
}

// split cpu mem and mmio
class ysyxSoCASIC(implicit p: Parameters) extends LazyModule {
  private val slavePortParamsOpt = p(ExtIn)
  private val mmioPortParamsOpt = p(ExtBus)
  private val memPortParamsOpt = p(ExtMem)
  private val device = new SimpleBus("axi4".kebab, Nil)
  val idBits = 4

  val chipMaster = LazyModule(new ChipLinkMaster)
  val xbar = AXI4Xbar()

  val cpuMemMaster = AXI4MasterNode(slavePortParamsOpt.map(params =>
    AXI4MasterPortParameters(
      masters = Seq(AXI4MasterParameters(
        name = "cpu",
        id   = IdRange(0, 1 << idBits))))).toSeq)

  val cpuMMIOMaster = AXI4MasterNode(slavePortParamsOpt.map(params =>
    AXI4MasterPortParameters(
      masters = Seq(AXI4MasterParameters(
        name = "cpu",
        id   = IdRange(0, 1 << idBits))))).toSeq)

  val chiplinkNode = AXI4SlaveNode(mmioPortParamsOpt.map(params =>
    AXI4SlavePortParametersGenerator(params,
      ChipLinkParam.mmio.base, ChipLinkParam.mmio.mask + 1 + ChipLinkParam.mem.mask + 1)).toSeq)

//  val chiplink_memNode = AXI4SlaveNode(memPortParamsOpt.map { case MemoryPortParams(params, _) =>
//    AXI4SlavePortParametersGenerator(params,
//    ChipLinkParam.mem.base, ChipLinkParam.mem.mask + 1)).toSeq)

  val spiNode = AXI4SlaveNode(mmioPortParamsOpt.map(params =>
    AXI4SlavePortParametersGenerator(params, 0x10000000, 0x10001000)).toSeq)

  val uartNode = AXI4SlaveNode(mmioPortParamsOpt.map(params =>
    AXI4SlavePortParametersGenerator(params, 0x20001000, 0x1000)).toSeq)

  List(chiplinkNode, spiNode, uartNode).map(_ := xbar)
  xbar := cpuMMIOMaster
  xbar := cpuMemMaster

  override lazy val module = new LazyModuleImp(this) with DontTouch {
    // expose cpu master interface as ports
    val cpu_mem  = IO(Flipped(HeterogeneousBag.fromNode(cpuMemMaster.out)))
    val cpu_mmio = IO(Flipped(HeterogeneousBag.fromNode(cpuMMIOMaster.out)))
    (cpuMemMaster.out  zip cpu_mem ) foreach { case ((bundle, _), io) => bundle <> io }
    (cpuMMIOMaster.out zip cpu_mmio) foreach { case ((bundle, _), io) => bundle <> io }

    // expose chiplink fpga I/O interface as ports
    val fpga_io = IO(chiselTypeOf(chipMaster.module.fpga_io))
    fpga_io <> chipMaster.module.fpga_io

    // connect chiplink slave interface to crossbar
    (chipMaster.slave zip chiplinkNode.in) foreach { case (io, (bundle, _)) => io <> bundle }

    // expose chiplink dma interface as ports
    val chiplink_dma = chipMaster.master_mem(0)
    val cpu_dma = IO(chiselTypeOf(chiplink_dma))
    cpu_dma <> chiplink_dma

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

  override lazy val module = new LazyModuleImp(this) with DontTouch {
    val fpga = LazyModule(new ysyxSoCFPGA)
    val mfpga = Module(fpga.module)

    asic.module.fpga_io.b2c <> mfpga.fpga_io.c2b
    mfpga.fpga_io.b2c <> asic.module.fpga_io.c2b

    (fpga.master_mem zip fpga.axi4MasterMemNode.in).map { case (io, (_, edge)) =>
      val mem = LazyModule(new SimAXIMem(edge,
        base = ChipLinkParam.mem.base, size = ChipLinkParam.mem.mask + 1))
      Module(mem.module)
      mem.io_axi4.head <> io
    }

    fpga.master_mmio.map(_.tieoff())
    fpga.slave.map(_.tieoff())

    val cpu_mem  = IO(chiselTypeOf(asic.module.cpu_mem))
    val cpu_mmio = IO(chiselTypeOf(asic.module.cpu_mmio))
    val cpu_dma  = IO(chiselTypeOf(asic.module.cpu_dma))
//    soc.module.cpu_master <> cpu_master
    asic.module.cpu_mem <> cpu_mem
    asic.module.cpu_mmio <> cpu_mmio
    cpu_dma <> asic.module.cpu_dma

    val spi  = IO(chiselTypeOf(asic.module.spi))
    val uart = IO(chiselTypeOf(asic.module.uart))
    spi  <> asic.module.spi
    uart <> asic.module.uart
  }
}
