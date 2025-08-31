package ysyx

import chisel3._
import chisel3.util._

import freechips.rocketchip.amba.apb._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy._

class apb4_archinfo extends BlackBox {
  val io = IO(new Bundle {
    val apb4_pclk = Input(Clock())
    val apb4_presetn = Input(Bool())
    val apb4 = Flipped(new APBBundle(APBBundleParameters(addrBits = 32, dataBits = 32)))
  })
}

class APB4ArchInfo(address: Seq[AddressSet])(implicit p: Parameters) extends LazyModule {
  val node = APBSlaveNode(Seq(APBSlavePortParameters(
    Seq(APBSlaveParameters(
      address       = address,
      executable    = true,
      supportsRead  = true,
      supportsWrite = true)),
    beatBytes  = 4)))

  lazy val module = new Impl
  class Impl extends LazyModuleImp(this) {
    val (in, _) = node.in(0)

    val m = Module(new apb4_archinfo)
    m.io.apb4_pclk := clock
    m.io.apb4_presetn := ~reset.asBool
    m.io.apb4 <> in
  }
}
