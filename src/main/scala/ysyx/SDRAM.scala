package ysyx

import chisel3._
import chisel3.experimental._

import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._

class SDRAMIO extends Bundle {
    val clk         = Output(Bool())
    val cke         = Output(Bool())
    val cs          = Output(Bool())
    val ras         = Output(Bool())
    val cas         = Output(Bool())
    val we          = Output(Bool())
    val dqm         = Output(UInt(2.W))
    val addr        = Output(UInt(13.W))
    val ba          = Output(UInt(2.W))
    val data_input  = Input(UInt(16.W))
    val data_output = Output(UInt(16.W))
    val data_out_en = Output(Bool())
}

class sdram_top extends BlackBox {
    val io = IO(new Bundle {
        val clock  = Input(Clock())
        val reset  = Input(Bool())
        val inport = Flipped(new AXI4Bundle(CPUAXI4BundleParameters()))
        val sdram  = new SDRAMIO
    })
}

class sdr_top extends BlackBox {
    val io = IO(Flipped(new SDRAMIO))
}

class AXI4SDRAM(address: Seq[AddressSet])(implicit p: Parameters) extends LazyModule {
  val node = AXI4SlaveNode(Seq(AXI4SlavePortParameters(
    Seq(AXI4SlaveParameters(
      address       = address,
      executable    = false,
      supportsRead  = TransferSizes(1, 8),
      supportsWrite = TransferSizes.none)),
    beatBytes  = 8))) // for support same beatBytes xbar impl

    lazy val module = new LazyModuleImp(this) {
        val (in, _) = node.in(0)
        val io = IO(new SDRAMIO)
        val msdram = Module(new sdram_top)
        msdram.io.clock := clock;
        msdram.io.reset := reset.asBool
        msdram.io.sdram <> io
        msdram.io.inport <> in
    }
}