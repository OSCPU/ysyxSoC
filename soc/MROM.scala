package ysyx

import chisel3._
import chisel3.util._

import freechips.rocketchip.amba.axi4._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util._

class MROMHelper extends BlackBox with HasBlackBoxInline {
  val io = IO(new Bundle {
    val raddr = Input(UInt(32.W))
    val ren = Input(Bool())
    val rdata = Output(UInt(32.W))
  })
  setInline("MROMHelper.v",
    """module MROMHelper(
      |  input [31:0] raddr,
      |  input ren,
      |  output reg [31:0] rdata
      |);
      |import "DPI-C" function void mrom_read(input int raddr, output int rdata);
      |always @(*) begin
      |  if (ren) mrom_read(raddr, rdata);
      |  else rdata = 0;
      |end
      |endmodule
    """.stripMargin)
}

class AXI4MROM(address: Seq[AddressSet])(implicit p: Parameters) extends LazyModule {
  val beatBytes = 8
  val node = AXI4SlaveNode(Seq(AXI4SlavePortParameters(
    Seq(AXI4SlaveParameters(
        address       = address,
        executable    = true,
        supportsWrite = TransferSizes.none,
        supportsRead  = TransferSizes(1, beatBytes),
        interleavedId = Some(0))
    ),
    beatBytes  = beatBytes)))

  private val outer = this

  lazy val module = new Impl
  class Impl extends LazyModuleImp(this) {
    val (in, _) = node.in(0)

    val mrom = Module(new MROMHelper)

    val (stateIdle, stateWaitRready) = (0.U, 1.U)
    val state = RegInit(stateIdle)
    state := Mux(state === stateIdle,
               Mux(in.ar.fire, stateWaitRready, stateIdle),
               Mux(in. r.fire, stateIdle, stateWaitRready))

    mrom.io.raddr := in.ar.bits.addr
    mrom.io.ren := in.ar.fire
    in.ar.ready := (state === stateIdle)
    assert(!(in.ar.fire && in.ar.bits.size === 3.U), "do not support 8 byte transfter")

    in.r.bits.data := RegEnable(Fill(2, mrom.io.rdata), in.ar.fire)
    in.r.bits.id := RegEnable(in.ar.bits.id, in.ar.fire)
    in.r.bits.resp := 0.U
    in.r.bits.last := true.B
    in.r.valid := (state === stateWaitRready)

    in.aw.ready := false.B
    in. w.ready := false.B
    in. b.valid := false.B

    assert(!in.aw.valid, "do not support write operations")
    assert(!in. w.valid, "do not support write operations")
  }
}
