package ysyx

import chisel3._
import chisel3.util._

import freechips.rocketchip.subsystem._
import freechips.rocketchip.config.{Field, Parameters}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.system._

import sifive.blocks.devices.chiplink._


object ChipLinkParam {
  // Must have a cacheable address sapce.
  val mem  = AddressSet(0x80000000L, 0x80000000L - 1)
  val mmio = AddressSet(0x40000000L, 0x40000000L - 1)
}


class LinkTopBase(implicit p: Parameters) extends LazyModule {
  val mbus = TLXbar()
  val fxbar = TLXbar()
  val ferr = LazyModule(new TLError(DevNullParams(Seq(AddressSet(0x1000L, 0x1000L - 1)), 64, 64, region = RegionType.TRACKED)))

  val chiplinkParam = ChipLinkParams(
    TLUH = List(ChipLinkParam.mmio),
    TLC  = List(ChipLinkParam.mem),
    syncTX = true
  )

  val chiplink = LazyModule(new ChipLink(chiplinkParam))
  val sink = chiplink.ioNode.makeSink

  chiplink.node := fxbar
  ferr.node := fxbar

  override lazy val module = new LinkTopBaseImpl(this)
}

class LinkTopBaseImpl[+L <: LinkTopBase](_outer: L) extends LazyModuleImp(_outer) {
  val outer = _outer
  val fpga_io = outer.sink.makeIO()
}


trait CanHaveAXI4MasterMemPortForLinkTop { this: LinkTopBase =>
  val module: CanHaveAXI4MasterMemPortModuleImpForLinkTop

  val axi4MasterMemNode = p(ExtMem).map { case MemoryPortParams(memPortParams, nMemoryChannels) =>
    val portName = "axi4"
    val device = new MemoryDevice

    val cacheBlockBytes = 64
    val axi4MasterMemNode = AXI4SlaveNode(Seq.tabulate(nMemoryChannels) { channel =>
      val base = ChipLinkParam.mem
      val filter = AddressSet(channel * cacheBlockBytes, ~((nMemoryChannels-1) * cacheBlockBytes))

      AXI4SlavePortParameters(
        slaves = Seq(AXI4SlaveParameters(
          address       = base.intersect(filter).toList,
          resources     = device.reg,
          regionType    = RegionType.UNCACHED, // cacheable
          executable    = true,
          supportsWrite = TransferSizes(1, cacheBlockBytes),
          supportsRead  = TransferSizes(1, cacheBlockBytes),
          interleavedId = Some(0))), // slave does not interleave read responses
        beatBytes = memPortParams.beatBytes)
    })

    axi4MasterMemNode := AXI4UserYanker() := AXI4IdIndexer(memPortParams.idBits) := TLToAXI4() := mbus

    axi4MasterMemNode
  }
}

trait CanHaveAXI4MasterMemPortModuleImpForLinkTop extends LazyModuleImp {
  val outer: CanHaveAXI4MasterMemPortForLinkTop

  val master_mem = outer.axi4MasterMemNode.map(x => IO(HeterogeneousBag.fromNode(x.in)))
  (master_mem zip outer.axi4MasterMemNode) foreach { case (io, node) =>
    (io zip node.in).foreach { case (io, (bundle, _)) => io <> bundle }
  }
}


/** Adds an AXI4 port to the system intended to be a slave on an MMIO device bus */
trait CanHaveAXI4SlaveMemPortForLinkTop { this: LinkTopBase =>
  implicit val p: Parameters

  private val slavePortParamsOpt = p(ExtIn)
  private val portName = "slave_port_axi4_mem"
  private val fifoBits = 1

  private val idBits = 4
  val axi4SlaveMemNode = AXI4MasterNode(
    slavePortParamsOpt.map(params =>
      AXI4MasterPortParameters(
        masters = Seq(AXI4MasterParameters(
          name = portName.kebab,
          id   = IdRange(0, 1 << idBits))))).toSeq)

  slavePortParamsOpt.map { params =>
    fxbar := TLFIFOFixer(TLFIFOFixer.all) := (TLWidthWidget(params.beatBytes)
      := AXI4ToTL()
      := AXI4UserYanker(Some(1 << (params.sourceBits - fifoBits - 1)))
      := AXI4Fragmenter()
      := AXI4IdIndexer(fifoBits)
      := axi4SlaveMemNode)
  }
}

/** Actually generates the corresponding IO in the concrete Module */
trait CanHaveAXI4SlaveMemPortModuleImpForLinkTop extends LazyModuleImp {
  val outer: CanHaveAXI4SlaveMemPortForLinkTop
  val slave_mem = IO(Flipped(HeterogeneousBag.fromNode(outer.axi4SlaveMemNode.out)))
  (outer.axi4SlaveMemNode.out zip slave_mem) foreach { case ((bundle, _), io) => bundle <> io }
}


/** Adds an AXI4 port to the system intended to be a slave on an MMIO device bus */
trait CanHaveAXI4SlaveMMIOPortForLinkTop { this: LinkTopBase =>
  implicit val p: Parameters

  private val slavePortParamsOpt = p(ExtIn)
  private val portName = "slave_port_axi4_mmio"
  private val fifoBits = 1

  val idBits = 4
  val axi4SlaveMMIONode = AXI4MasterNode(
    slavePortParamsOpt.map(params =>
      AXI4MasterPortParameters(
        masters = Seq(AXI4MasterParameters(
          name = portName.kebab,
          id   = IdRange(0, 1 << idBits))))).toSeq)

  slavePortParamsOpt.map { params =>
    fxbar := TLFIFOFixer(TLFIFOFixer.all) := (TLWidthWidget(params.beatBytes)
      := AXI4ToTL()
      := AXI4UserYanker(Some(1 << (params.sourceBits - fifoBits - 1)))
      := AXI4Fragmenter()
      := AXI4IdIndexer(fifoBits)
      := axi4SlaveMMIONode)
  }
}

/** Actually generates the corresponding IO in the concrete Module */
trait CanHaveAXI4SlaveMMIOPortModuleImpForLinkTop extends LazyModuleImp {
  val outer: CanHaveAXI4SlaveMMIOPortForLinkTop
  val slave_mmio = IO(Flipped(HeterogeneousBag.fromNode(outer.axi4SlaveMMIONode.out)))
  (outer.axi4SlaveMMIONode.out zip slave_mmio) foreach { case ((bundle, _), io) => bundle <> io }
}


/** Adds a AXI4 port to the system intended to master an MMIO device bus */
trait CanHaveAXI4MasterMMIOPortForLinkTop { this: LinkTopBase =>
  implicit val p: Parameters

  private val mmioPortParamsOpt = p(ExtBus)
  private val portName = "mmio_port_axi4"
  private val device = new SimpleBus(portName.kebab, Nil)

  val axi4MasterMMIONode = AXI4SlaveNode(
    mmioPortParamsOpt.map(params =>
      AXI4SlavePortParameters(
        slaves = Seq(AXI4SlaveParameters(
          address       = Seq(ChipLinkParam.mmio),
          resources     = device.ranges,
          executable    = params.executable,
          supportsWrite = TransferSizes(1, params.maxXferBytes),
          supportsRead  = TransferSizes(1, params.maxXferBytes))),
        beatBytes = params.beatBytes)).toSeq)

  mmioPortParamsOpt.map { params =>
    axi4MasterMMIONode := (AXI4Buffer()
      := AXI4UserYanker()
      := AXI4Deinterleaver(64 /* blockBytes, literal OK? */)
      := AXI4IdIndexer(params.idBits)
      := TLToAXI4()) := mbus
  }
}


/** Actually generates the corresponding IO in the concrete Module */
trait CanHaveAXI4MasterMMIOPortModuleImpForLinkTop extends LazyModuleImp {
  val outer: CanHaveAXI4MasterMMIOPortForLinkTop
  val master_mmio = IO(HeterogeneousBag.fromNode(outer.axi4MasterMMIONode.in))
  (master_mmio zip outer.axi4MasterMMIONode.in) foreach { case (io, (bundle, _)) => io <> bundle }
}


class ChipLinkMaster(implicit p: Parameters) extends LinkTopBase
  with CanHaveAXI4SlaveMemPortForLinkTop
  with CanHaveAXI4SlaveMMIOPortForLinkTop
  with CanHaveAXI4MasterMemPortForLinkTop
{
  // Dummy manager network
  val err = LazyModule(new TLError(DevNullParams(Seq(AddressSet(0x1000L, 0x1000L - 1)), 64, 64, region = RegionType.TRACKED)))

  // Hint & Atomic augment
  mbus := TLAtomicAutomata(passthrough=false) := TLFIFOFixer(TLFIFOFixer.all) := TLHintHandler() := TLWidthWidget(4) := chiplink.node
  err.node := TLWidthWidget(8) := mbus

  override lazy val module = new LinkTopBaseImpl(this)
    with CanHaveAXI4SlaveMemPortModuleImpForLinkTop
    with CanHaveAXI4SlaveMMIOPortModuleImpForLinkTop
    with CanHaveAXI4MasterMemPortModuleImpForLinkTop
    with DontTouch
}


/**
  * Dual top module against Rocketchip over rx/tx channel.
  */
class ChipLinkSlave(implicit p: Parameters) extends LinkTopBase
  with CanHaveAXI4MasterMemPortForLinkTop
  with CanHaveAXI4MasterMMIOPortForLinkTop
  with CanHaveAXI4SlaveMemPortForLinkTop
{
  // Dummy manager network
  val err = LazyModule(new TLError(DevNullParams(Seq(AddressSet(0x1000L, 0x1000L - 1)), 64, 64, region = RegionType.TRACKED)))

  // Hint & Atomic augment
  mbus := TLAtomicAutomata(passthrough=false) := TLFIFOFixer(TLFIFOFixer.all) := TLHintHandler() := TLWidthWidget(4) := chiplink.node
  err.node := TLWidthWidget(8) := mbus

  override lazy val module = new LinkTopBaseImpl(this)
    with CanHaveAXI4MasterMemPortModuleImpForLinkTop
    with CanHaveAXI4MasterMMIOPortModuleImpForLinkTop
    with CanHaveAXI4SlaveMemPortModuleImpForLinkTop
    with DontTouch
}
