package ysyx

import chisel3._
import chisel3.util._

import freechips.rocketchip.config.Parameters
import freechips.rocketchip.amba._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.amba.apb._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util._

case class AXI4ToAPBNode()(implicit valName: ValName) extends MixedAdapterNode(AXI4Imp, APBImp)(
  dFn = { mp =>
    APBMasterPortParameters(
      masters = mp.masters.map { m => APBMasterParameters(name = m.name, nodePath = m.nodePath) },
      requestFields = mp.requestFields.filter(!_.isInstanceOf[AMBAProtField]),
      responseKeys  = mp.responseKeys
    )
  },
  uFn = { sp =>
    val beatBytes = 8
    AXI4SlavePortParameters(
    slaves = sp.slaves.map { s =>
      val maxXfer = TransferSizes(1, beatBytes)
      require(beatBytes == 8) // only support 8-byte data AXI
      AXI4SlaveParameters(
        address       = s.address,
        resources     = s.resources,
        regionType    = s.regionType,
        executable    = s.executable,
        nodePath      = s.nodePath,
        supportsWrite = if (s.supportsWrite) TransferSizes(1, beatBytes) else TransferSizes.none,
        supportsRead  = if (s.supportsRead)  TransferSizes(1, beatBytes) else TransferSizes.none,
        interleavedId = Some(0))}, // never interleaves D beats
    beatBytes = beatBytes,
    responseFields = sp.responseFields,
    requestKeys    = sp.requestKeys.filter(_ != AMBAProt))
  }
)

class AXI4ToAPB(val aFlow: Boolean = true)(implicit p: Parameters) extends LazyModule {
  val node = AXI4ToAPBNode()

  lazy val module = new LazyModuleImp(this) {
    (node.in zip node.out) foreach { case ((in, edgeIn), (out, edgeOut)) =>
      // We need a skidpad to capture D output:
      // We cannot know if the D response will be accepted until we have
      // presented it on D as valid.  We also can't back-pressure APB in the
      // data phase.  Therefore, we must have enough space to save the data
      // phase result.  Whenever we have a queued response, we can not allow
      // APB to present new responses, so we must quash the address phase.
      val r = WireInit(in.r)
      in.r <> Queue(r, 1, flow = true)
      val b = WireInit(in.b)
      in.b <> Queue(b, 1, flow = true)

      // We need an irrevocable input for APB to stall
      val ar = Queue(in.ar, 1, flow = aFlow, pipe = !aFlow)
      val aw = Queue(in.aw, 1, flow = aFlow, pipe = !aFlow)
      val w  = Queue(in.w , 1, flow = aFlow, pipe = !aFlow)

      // burst is not supported
      assert(!(ar.valid && ar.bits.len =/= 0.U))
      assert(!(aw.valid && aw.bits.len =/= 0.U))
      // size > 4 is not supported
      assert(!(ar.valid && ar.bits.size > "b10".U))
      assert(!(aw.valid && aw.bits.size > "b10".U))

      val ar_enable = RegInit(false.B)
      val aw_enable = RegInit(false.B)
      // read can not preempt write
      val ar_sel    = ar.valid && !aw_enable && RegNext(!in.r.valid || in.r.ready)
      // read has higher priority than write in the same cycle
      val aw_sel    = aw.valid && w.valid && !ar_sel && RegNext(!in.b.valid || in.b.ready)

      val enable_r = ar_sel && !ar_enable
      val r_id     = RegEnable(ar.bits.id, enable_r)
      val enable_b = aw_sel && !aw_enable
      val b_id     = RegEnable(aw.bits.id, enable_b)

      when (ar_sel)   { ar_enable := true.B }
      when (r.fire()) { ar_enable := false.B }
      when (aw_sel)   { aw_enable := true.B }
      when (b.fire()) { aw_enable := false.B }

      val araddr = ar.bits.addr
      val awaddr = aw.bits.addr
      val wdata  = w.bits.data
      val wstrb  = w.bits.strb

      out.psel    := ar_sel || aw_sel
      out.penable := ar_enable || aw_enable
      out.pwrite  := aw_sel
      out.paddr   := Mux(ar_sel, araddr, awaddr)
      out.pprot   := APBParameters.PROT_DEFAULT
      out.pwdata  := Mux(awaddr(2), wdata(63,32), wdata(31,0))
      out.pstrb   := Mux(ar_sel, 0.U, Mux(awaddr(2), wstrb(7,4), wstrb(3,0)))

      ar.ready := ar_enable && out.pready
      r.valid  := ar_enable && out.pready
      assert (!r.valid || r.ready)

      aw.ready := aw_enable && out.pready
      w.ready  := aw_enable && out.pready
      b.valid  := aw_enable && out.pready
      assert (!b.valid || b.ready)

      // me below
      r.bits.data := Cat(out.prdata, out.prdata)
      r.bits.id   := r_id
      r.bits.resp := Mux(out.pslverr, AXI4Parameters.RESP_SLVERR, AXI4Parameters.RESP_OKAY)
      r.bits.last := true.B

      b.bits.resp := Mux(out.pslverr, AXI4Parameters.RESP_SLVERR, AXI4Parameters.RESP_OKAY)
      b.bits.id   := b_id
    }
  }
}

object AXI4ToAPB {
  def apply(aFlow: Boolean = true)(implicit p: Parameters) = {
    val axi42apb = LazyModule(new AXI4ToAPB(aFlow))
    axi42apb.node
  }
}
