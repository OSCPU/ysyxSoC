package ysyx

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.Parameters
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
    val beatBytes = 4
    AXI4SlavePortParameters(
    slaves = sp.slaves.map { s =>
      val maxXfer = TransferSizes(1, beatBytes)
      require(beatBytes == 4) // only support 8-byte data AXI
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
      val (ar, r, aw, w, b) = (in.ar, in.r, in.aw, in.w, in.b)

      val s_idle :: s_buf :: s_inflight :: s_wait_rready_bready :: Nil = Enum(4)
      val state = RegInit(s_idle)
      val accept_read = (state === s_idle) && ar.valid
      val accept_write = !accept_read && (state === s_idle) && aw.valid && w.valid
      val is_write = RegEnable(accept_write, state === s_idle)
      switch (state) {
        is (s_idle)     { state := Mux(ar.valid || (aw.valid && w.valid), s_buf, s_idle) }
        is (s_buf)      { state := s_inflight }
        is (s_inflight) { state := Mux(out.pready, Mux(r.fire || b.fire, s_idle, s_wait_rready_bready), s_inflight) }
        is (s_wait_rready_bready) { state := Mux(r.fire || b.fire, s_idle, s_wait_rready_bready) }
      }

      // burst is not supported
      assert(!(ar.valid && ar.bits.len =/= 0.U))
      assert(!(aw.valid && aw.bits.len =/= 0.U))
      // size > 4 is not supported
      assert(!(ar.valid && ar.bits.size > "b10".U))
      assert(!(aw.valid && aw.bits.size > "b10".U))

      val rid_reg    = RegEnable(ar.bits.id, accept_read)
      val bid_reg    = RegEnable(aw.bits.id, accept_write)
      val araddr_reg = RegEnable(ar.bits.addr, accept_read)
      val awaddr_reg = RegEnable(aw.bits.addr, accept_write)
      val wdata_reg  = RegEnable( w.bits.data, accept_write)
      val wstrb_reg  = RegEnable( w.bits.strb, accept_write)

      out.psel    := (state === s_buf) || out.penable
      out.penable := state === s_inflight
      out.pwrite  := is_write
      out.paddr   := Mux(is_write, awaddr_reg, araddr_reg)
      out.pprot   := APBParameters.PROT_DEFAULT
      out.pwdata  := wdata_reg
      out.pstrb   := Mux(is_write, wstrb_reg, 0.U)

      ar.ready := accept_read
      w.ready  := accept_write
      aw.ready := accept_write

      val resp = Mux(out.pslverr, AXI4Parameters.RESP_SLVERR, AXI4Parameters.RESP_OKAY)
      val resp_hold = resp holdUnless (state === s_inflight)
      r.valid  := !is_write && (((state === s_inflight) && out.pready) || (state === s_wait_rready_bready))
      r.bits.data := Fill(2, out.prdata holdUnless (state === s_inflight))
      r.bits.id   := rid_reg
      r.bits.resp := resp_hold
      r.bits.last := true.B

      b.valid  := is_write && (((state === s_inflight) && out.pready) || (state === s_wait_rready_bready))
      b.bits.resp := resp_hold
      b.bits.id   := bid_reg
    }
  }
}

object AXI4ToAPB {
  def apply(aFlow: Boolean = true)(implicit p: Parameters) = {
    val axi42apb = LazyModule(new AXI4ToAPB(aFlow))
    axi42apb.node
  }
}
