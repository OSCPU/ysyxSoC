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
  val allSpace = Seq(mem, mmio)
  val idBits = 4
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
  private val portName = "axi4"
  private val device = new MemoryDevice
  private val cacheBlockBytes = 64
  private val idBits = ChipLinkParam.idBits

  val axi4MasterMemNode = AXI4SlaveNode(p(ExtMem).map { case MemoryPortParams(memPortParams, nMemoryChannels) =>
    Seq.tabulate(nMemoryChannels) { channel =>
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
    }
  }.toList.flatten)

  axi4MasterMemNode := AXI4UserYanker() := AXI4IdIndexer(idBits) := TLToAXI4() := mbus

  val master_mem = InModuleBody { axi4MasterMemNode.makeIOs() }
}


/** Adds an AXI4 port to the system intended to be a slave on an MMIO device bus */
trait CanHaveAXI4SlavePortForLinkTop { this: LinkTopBase =>
  implicit val p: Parameters

  private val slavePortParamsOpt = p(ExtIn)
  private val portName = "slave_port_axi4_mem"
  private val fifoBits = 1
  private val idBits = ChipLinkParam.idBits

  val axi4SlaveNode = AXI4MasterNode(
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
      := axi4SlaveNode)
  }

  val slave = InModuleBody { axi4SlaveNode.makeIOs() }
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
      := AXI4IdIndexer(ChipLinkParam.idBits)
      := TLToAXI4()) := mbus
  }

  val master_mmio = InModuleBody { axi4MasterMMIONode.makeIOs() }
}


class ChipLinkMaster(implicit p: Parameters) extends LinkTopBase
  with CanHaveAXI4SlavePortForLinkTop
  with CanHaveAXI4MasterMemPortForLinkTop
{
  // Dummy manager network
  val err = LazyModule(new TLError(DevNullParams(Seq(AddressSet(0x1000L, 0x1000L - 1)), 64, 64, region = RegionType.TRACKED)))

  // Hint & Atomic augment
  mbus := TLAtomicAutomata(passthrough=false) := TLFIFOFixer(TLFIFOFixer.all) := TLHintHandler() := TLWidthWidget(4) := chiplink.node
  err.node := TLWidthWidget(8) := mbus

  override lazy val module = new LinkTopBaseImpl(this) with DontTouch
}


/**
  * Dual top module against Rocketchip over rx/tx channel.
  */
class ChipLinkSlave(implicit p: Parameters) extends LinkTopBase
  with CanHaveAXI4MasterMemPortForLinkTop
  with CanHaveAXI4MasterMMIOPortForLinkTop
  with CanHaveAXI4SlavePortForLinkTop
{
  // Dummy manager network
  val err = LazyModule(new TLError(DevNullParams(Seq(AddressSet(0x1000L, 0x1000L - 1)), 64, 64, region = RegionType.TRACKED)))

  // Hint & Atomic augment
  mbus := TLAtomicAutomata(passthrough=false) := TLFIFOFixer(TLFIFOFixer.all) := TLHintHandler() := TLWidthWidget(4) := chiplink.node
  err.node := TLWidthWidget(8) := mbus

  override lazy val module = new LinkTopBaseImpl(this) with DontTouch
}
