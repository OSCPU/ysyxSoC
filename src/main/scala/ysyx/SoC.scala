package ysyx

import chisel3._
import chisel3.util._

import freechips.rocketchip.diplomacy._
import freechips.rocketchip.config.{Field, Parameters}
import freechips.rocketchip.subsystem._
import freechips.rocketchip.util._
import freechips.rocketchip.amba.axi4._

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

  val cpuMMIOMaster = AXI4MasterNode( slavePortParamsOpt.map(params =>
    AXI4MasterPortParameters(
      masters = Seq(AXI4MasterParameters(
        name = "cpu",
        id   = IdRange(0, 1 << idBits))))).toSeq)

  val chiplink_mmioNode = AXI4SlaveNode(mmioPortParamsOpt.map(params =>
    AXI4SlavePortParametersGenerator(params,
      ChipLinkParam.mmio.base, ChipLinkParam.mmio.mask + 1)).toSeq)

//  val chiplink_memNode = AXI4SlaveNode(memPortParamsOpt.map { case MemoryPortParams(params, _) =>
//    AXI4SlavePortParametersGenerator(params,
//    ChipLinkParam.mem.base, ChipLinkParam.mem.mask + 1)).toSeq)

  val spiNode = AXI4SlaveNode(mmioPortParamsOpt.map(params =>
    AXI4SlavePortParametersGenerator(params, 0x10000000, 0x10001000)).toSeq)

  val uartNode = AXI4SlaveNode(mmioPortParamsOpt.map(params =>
    AXI4SlavePortParametersGenerator(params, 0x20001000, 0x1000)).toSeq)

  List(chiplink_mmioNode, spiNode, uartNode).map(_ := xbar)
  xbar := cpuMMIOMaster

  override lazy val module = new LazyModuleImp(this) with DontTouch {
    // expose cpu master interface as ports
    val cpu_mmio = IO(Flipped(HeterogeneousBag.fromNode(cpuMMIOMaster.out)))
    (cpuMMIOMaster.out zip cpu_mmio) foreach { case ((bundle, _), io) => bundle <> io }

    // expose chiplink fpga I/O interface as ports
    val fpga_io = IO(chiselTypeOf(chipMaster.module.fpga_io))
    fpga_io <> chipMaster.module.fpga_io

    // connect chiplink mmio interface to crossbar
    (chipMaster.module.slave_mmio zip chiplink_mmioNode.in) foreach { case (io, (bundle, _)) => io <> bundle }

    // expose chiplink mem interface as ports
    val chiplink_mem = chipMaster.module.slave_mem
    val cpu_mem = IO(chiselTypeOf(chiplink_mem))
    chiplink_mem <> cpu_mem

    // expose chiplink dma interface as ports
    val chiplink_dma = chipMaster.module.master_mem.get
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
