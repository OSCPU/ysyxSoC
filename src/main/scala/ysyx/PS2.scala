package ysyx

import chisel3._

import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._


class PS2IO extends Bundle {
    val clk = Input(Bool())
    val dat = Input(Bool())
}

class ps2 extends BlackBox {
  val io = IO(new Bundle {
    val clock = Input(Clock())
    val resetn = Input(Bool())
    val io_slave = Flipped(new AXI4Bundle(CPUAXI4BundleParameters()))
    val ps2 = new PS2IO
  })
}

class kdb extends BlackBox {
    val io = IO(new Bundle {
      val clock = Input(Clock())
      val resetn = Input(Bool())
      val ps2 = Flipped(new PS2IO)
    })
}

class AXI4PS2(address: Seq[AddressSet])(implicit p: Parameters) extends LazyModule {
  val node = AXI4SlaveNode(Seq(AXI4SlavePortParameters(
    Seq(AXI4SlaveParameters(
      address       = address,
      executable    = false,
      supportsRead  = TransferSizes(1, 8),
      supportsWrite = TransferSizes.none)),
    beatBytes  = 8))) // for support same beatBytes xbar impl

    lazy val module = new LazyModuleImp(this) {
        val (in, _) = node.in(0)
        val io = IO(new PS2IO)
        val mps2 = Module(new ps2)
        mps2.io.clock := clock;
        mps2.io.resetn := ~reset.asBool
        mps2.io.io_slave <> in
        mps2.io.ps2 <> io
    }
}
