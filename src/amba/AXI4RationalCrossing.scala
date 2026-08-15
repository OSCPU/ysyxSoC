// See LICENSE.SiFive for license details.

// If you know two clocks are related with a N:1 or 1:N relationship, you
// can cross the clock domains with lower latency than an AsyncQueue.
// This clock crossing behaves almost identically to a AXI4Buffer(2):
//   - It adds one cycle latency to each clock domain.
//   - All outputs of AXI4Rational are registers (bits, valid, and ready).
//   - It costs 3*bits registers as opposed to 2*bits in a AXI4Buffer(2)

package freechips.rocketchip.amba.axi4

import chisel3._
import chisel3.experimental.SourceInfo

import org.chipsalliance.cde.config._
import org.chipsalliance.diplomacy._
import org.chipsalliance.diplomacy.nodes._
import org.chipsalliance.diplomacy.lazymodule._

import freechips.rocketchip.diplomacy.{NodeHandle, SimpleNodeImp, MixedAdapterNode}
import freechips.rocketchip.util._

case class AXI4RationalSlavePortParameters(direction: RationalDirection, base: AXI4SlavePortParameters)
case class AXI4RationalMasterPortParameters(base: AXI4MasterPortParameters)

case class AXI4RationalEdgeParameters(master: AXI4RationalMasterPortParameters, slave: AXI4RationalSlavePortParameters, params: Parameters,sourceInfo: SourceInfo) {
  val bundle = AXI4BundleParameters(master.base, slave.base)
}

class AXI4RationalBundle(params: AXI4BundleParameters) extends Bundle {
  val aw = RationalIO(new AXI4BundleAW(params))
  val w  = RationalIO(new AXI4BundleW (params))
  val b  = Flipped(RationalIO(new AXI4BundleB (params)))
  val ar = RationalIO(new AXI4BundleAR(params))
  val r  = Flipped(RationalIO(new AXI4BundleR (params)))
}

object AXI4RationalImp extends SimpleNodeImp[AXI4RationalMasterPortParameters, AXI4RationalSlavePortParameters, AXI4RationalEdgeParameters, AXI4RationalBundle] {
  def edge(pd: AXI4RationalMasterPortParameters, pu: AXI4RationalSlavePortParameters, p: Parameters, sourceInfo: SourceInfo) = AXI4RationalEdgeParameters(pd, pu, p, sourceInfo)
  def bundle(e: AXI4RationalEdgeParameters) = new AXI4RationalBundle(e.bundle)
  def render(e: AXI4RationalEdgeParameters) = RenderedEdge(colour = "#00ff00" /* green */)

  override def mixO(pd: AXI4RationalMasterPortParameters, node: OutwardNode[AXI4RationalMasterPortParameters, AXI4RationalSlavePortParameters, AXI4RationalBundle]): AXI4RationalMasterPortParameters  =
   pd.copy(base = pd.base.copy(masters = pd.base.masters.map  { c => c.copy (nodePath = node +: c.nodePath) }))
  override def mixI(pu: AXI4RationalSlavePortParameters, node: InwardNode[AXI4RationalMasterPortParameters, AXI4RationalSlavePortParameters, AXI4RationalBundle]): AXI4RationalSlavePortParameters =
   pu.copy(base = pu.base.copy(slaves = pu.base.slaves.map { m => m.copy (nodePath = node +: m.nodePath) }))
}

case class AXI4RationalSourceNode()(implicit valName: ValName)
  extends MixedAdapterNode(AXI4Imp, AXI4RationalImp)(
    dFn = { p => AXI4RationalMasterPortParameters(p) },
    uFn = { p => p.base.copy(minLatency = 1) })
case class AXI4RationalSinkNode(direction: RationalDirection)(implicit valName: ValName)
  extends MixedAdapterNode(AXI4RationalImp, AXI4Imp)(
    dFn = { p => p.base },
    uFn = { p => AXI4RationalSlavePortParameters(direction, p) })

class AXI4RationalCrossingSource(implicit p: Parameters) extends LazyModule {
  val node = AXI4RationalSourceNode()
  lazy val module = new Impl
  class Impl extends LazyModuleImp(this) {
    (node.in zip node.out) foreach { case ((in, edgeIn), (out, edgeOut)) =>
      val direction = edgeOut.slave.direction
      out.ar <> ToRational(in.ar, direction)
      out.aw <> ToRational(in.aw, direction)
      out. w <> ToRational(in. w, direction)
      in .r  <> FromRational(out.r, direction.flip)
      in .b  <> FromRational(out.b, direction.flip)
    }
  }
}

class AXI4RationalCrossingSink(direction: RationalDirection = Symmetric)(implicit p: Parameters) extends LazyModule {
  val node = AXI4RationalSinkNode(direction)
  lazy val module = new Impl
  class Impl extends LazyModuleImp(this) {
    (node.in zip node.out) foreach { case ((in, edgeIn), (out, edgeOut)) =>
      val direction = edgeIn.slave.direction
      out.ar <> FromRational(in.ar, direction)
      out.aw <> FromRational(in.aw, direction)
      out. w <> FromRational(in. w, direction)
      in .r  <> ToRational(out.r, direction.flip)
      in .b  <> ToRational(out.b, direction.flip)
    }
  }
}

object AXI4RationalCrossingSource {
  def apply()(implicit p: Parameters) = LazyModule(new AXI4RationalCrossingSource).node
}

object AXI4RationalCrossingSink {
  def apply(direction: RationalDirection = Symmetric)(implicit p: Parameters) =
    LazyModule(new AXI4RationalCrossingSink(direction)).node
}

@deprecated("AXI4RationalCrossing is fragile. Use AXI4RationalCrossingSource and AXI4RationalCrossingSink", "rocket-chip 1.2")
class AXI4RationalCrossing(direction: RationalDirection = Symmetric)(implicit p: Parameters) extends LazyModule {
  val source = LazyModule(new AXI4RationalCrossingSource)
  val sink = LazyModule(new AXI4RationalCrossingSink(direction))
  val node = NodeHandle(source.node, sink.node)
  sink.node := source.node
  lazy val module = new Impl
  class Impl extends LazyModuleImp(this) {
    val io = IO(new Bundle {
      val in_clock  = Input(Clock())
      val in_reset  = Input(Bool())
      val out_clock = Input(Clock())
      val out_reset = Input(Bool())
    })
    source.module.clock := io.in_clock
    source.module.reset := io.in_reset
    sink.module.clock := io.out_clock
    sink.module.reset := io.out_reset
  }
}
