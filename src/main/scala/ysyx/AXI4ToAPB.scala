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
      val (ar, r, aw, w, b) = (in.ar, in.r, in.aw, in.w, in.b)

      val s_idle :: s_ar :: s_r :: s_aw :: s_w :: s_b :: Nil = Enum(6)
      val state = RegInit(s_idle)
      switch (state) {
        is (s_idle) { state := Mux(ar.valid, s_ar, Mux(aw.valid, s_aw, s_idle)) }
        is (s_ar)   { state := s_r }
        is (s_r)    { when (r.ready && out.pready) { state := s_idle } }
        is (s_aw)   { when (w.valid) { state := s_w } }
        is (s_w)    { when (out.pready) { state := s_b } }
        is (s_b)    { when (b.ready) { state := s_idle } }
      }
      val is_ar = (state === s_ar)
      val is_r  = (state === s_r)
      val is_aw = (state === s_aw)
      val is_w  = (state === s_w)
      val is_b  = (state === s_b)
      val is_write = is_aw || is_w || is_b

      // burst is not supported
      assert(!(ar.valid && ar.bits.len =/= 0.U))
      assert(!(aw.valid && aw.bits.len =/= 0.U))
      // size > 4 is not supported
      assert(!(ar.valid && ar.bits.size > "b10".U))
      assert(!(aw.valid && aw.bits.size > "b10".U))

      val rid_reg    = RegEnable(ar.bits.id, ar.fire())
      val bid_reg    = RegEnable(aw.bits.id, aw.fire())
      val araddr_reg = RegEnable(ar.bits.addr, ar.fire())
      val awaddr_reg = RegEnable(aw.bits.addr, aw.fire())
      val wdata_reg  = RegEnable(w.bits.data, w.fire())
      val wstrb_reg  = RegEnable(w.bits.strb, w.fire())

      out.psel    := is_r || is_w
      out.penable := out.psel && RegNext(out.psel)
      out.pwrite  := is_write
      out.paddr   := Mux(is_write, awaddr_reg, araddr_reg)
      out.pprot   := APBParameters.PROT_DEFAULT
      out.pwdata  := Mux(awaddr_reg(2), wdata_reg(63,32), wdata_reg(31,0))
      out.pstrb   := Mux(is_write, Mux(awaddr_reg(2), wstrb_reg(7,4), wstrb_reg(3,0)), 0.U)

      ar.ready := is_ar
      aw.ready := is_aw && !RegNext(is_aw)
      w.ready  := is_w  && !RegNext(is_w)

      r.valid  := is_r && out.pready
      r.bits.data := Cat(out.prdata, out.prdata)
      r.bits.id   := rid_reg
      r.bits.resp := Mux(out.pslverr, AXI4Parameters.RESP_SLVERR, AXI4Parameters.RESP_OKAY)
      r.bits.last := true.B

      b.valid  := is_b
      b.bits.resp := Mux(out.pslverr, AXI4Parameters.RESP_SLVERR, AXI4Parameters.RESP_OKAY)
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
