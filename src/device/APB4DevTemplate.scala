package ysyx

import chisel3._
import chisel3.util._

import freechips.rocketchip.amba.apb._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy._

object APBSlaveNodeGenerator {
  def apply(address: Seq[AddressSet])(implicit p: Parameters) =
  APBSlaveNode(Seq(APBSlavePortParameters(
    Seq(APBSlaveParameters(
      address       = address,
      executable    = true,
      supportsRead  = true,
      supportsWrite = true)),
    beatBytes  = 4)))
}

class APB4DevTemplate[T <: Data](address: Seq[AddressSet], extraIO: T = null)
  (body: (APBBundle, LazyModuleImp, T) => Unit)(implicit p: Parameters) extends LazyModule {
  val node = APBSlaveNodeGenerator(address)
  lazy val module = new Impl
  class Impl extends LazyModuleImp(this) {
    val (in, _) = node.in(0)
    val extra = if (extraIO != null) IO(Flipped(Flipped(extraIO))) else null.asInstanceOf[T]
    body(in, this, extra)
  }
}

trait WithMyAPB4Bundle {
  val apb4_pclk: Clock
  val apb4_presetn: Bool
  val apb4: APBBundle
  def connect_extra(extra: Data): Unit
}

class MyAPB4Bundle extends Bundle with WithMyAPB4Bundle {
  val apb4_pclk = Input(Clock())
  val apb4_presetn = Input(Bool())
  val apb4 = Flipped(new APBBundle(APBBundleParameters(addrBits = 32, dataBits = 32)))
  def connect_extra(extra: Data): Unit = {}
}

class BlackBoxWithAPB4[T <: Bundle with WithMyAPB4Bundle](gen: => T = new MyAPB4Bundle) extends BlackBox {
  val io = IO(gen)
}

class APB4DevBlackBox[T <: Data, U <: Bundle with WithMyAPB4Bundle]
  (address: Seq[AddressSet], blackboxGen: () => BlackBoxWithAPB4[U], extraIO: T = null)
  (implicit p: Parameters) extends APB4DevTemplate(address, extraIO)((in: APBBundle, outer: LazyModuleImp, extra) => {
  val m = Module(blackboxGen())
  m.io.apb4_pclk := outer.clock
  m.io.apb4_presetn := !outer.reset.asBool
  m.io.apb4 <> in
  if (extra != null) m.io.connect_extra(extra)
})
