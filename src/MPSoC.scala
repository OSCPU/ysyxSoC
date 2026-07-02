package ysyx

import chisel3._
import chisel3.util._
import chisel3.util.experimental.forceName
import freechips.rocketchip.util._

class TileBundle extends Bundle {
  val led = Output(UInt(8.W))
  val ledUpdate = Output(Bool())
  val btn = Input(UInt(8.W))
  val dip = Input(UInt(8.W))
  val hex7seg = Output(Vec(2, UInt(4.W)))
  val hex7segUpdate = Output(Bool())

  val customOut = Output(UInt(16.W))
  val customIn = Input(UInt(16.W))

  val ramAddr = Output(UInt(8.W))
  val ramWen = Output(Bool())
  val ramWdata = Output(UInt(8.W))
  val ramRdata = Input(UInt(8.W))
}

class Tile extends BlackBox {
  val io = IO(new Bundle {
    val clock = Input(Clock())
    val reset = Input(Reset())
    val io = new TileBundle
  })
}

class TileMultiplexer(numTile: Int) extends Module {
  val io = IO(new Bundle {
    val sel = Input(UInt(Config.tileSelWidth.W))
    val out = new TileBundle
    val in = Flipped(Vec(numTile, new TileBundle))
  })

  io.out := DontCare
  io.in.zipWithIndex.map { case (in, i) =>
    in := DontCare
    when (i.U === io.sel) {
      io.out <> in
    }
  }
}

class MPSoC extends Module {
  val tmp = Module(new TileMultiplexer(Config.numTile))
  tmp.io.in.zipWithIndex.map { case (in, i) =>
    val tile = Module(new Tile)
    forceName(tile, s"tile${i}")
    tile.io.clock := clock
    tile.io.reset := reset
    in <> tile.io.io
  }

  // memory
  val ram = Mem(256, UInt(8.W))
  tmp.io.out.ramRdata := ram(tmp.io.out.ramAddr)
  when (tmp.io.out.ramWen) {
    ram(tmp.io.out.ramAddr) := tmp.io.out.ramWdata
  }

  // expose I/O interface as ports
  def genIO[T <: Data](name: String, inner: T) = {
    val outer = IO(chiselTypeOf(inner))
    outer.suggestName(name)
    outer <> inner
    outer
  }

  val tileSel = genIO("tileSel", tmp.io.sel)
  val led = genIO("led", RegEnable(tmp.io.out.led, tmp.io.out.ledUpdate))
  val btn = genIO("btn", tmp.io.out.btn)
  val dip = genIO("dip", tmp.io.out.dip)
  val hex7seg = genIO("hex7seg", RegEnable(tmp.io.out.hex7seg, tmp.io.out.hex7segUpdate))
  val customOut = genIO("customOut", tmp.io.out.customOut)
  val customIn = genIO("customIn", tmp.io.out.customIn)
}

class MPSoCasicTop extends Module with DontTouch {
  val soc = Module(new MPSoC)

  def genPAD[T <: Data](name: String, inner: T)(implicit dir: PadDirection): T = {
    val outer = IO(chiselTypeOf(inner))
    outer.suggestName(name)
    GenPAD(outer, inner)
    dontTouch(outer)
    outer
  }

  val clock_pad_i = IO(Input(Clock()))
  val clock_pad_o = IO(Output(Clock()))
  GenPAD(clock_pad_i, soc.clock, clock_pad_o)(VPad())

  val resetn_pad_i = IO(Input(Bool()))
  val resetn = Wire(Bool())
  GenPAD(resetn_pad_i, resetn)(VPad())
  soc.reset := AsyncResetSynchronizer(soc.clock, ~resetn)

  val tileSel = genPAD("tileSel", soc.tileSel)(VPad())
  val led = genPAD("led", soc.led)(VPad())
  val btn = genPAD("btn", soc.btn)(HPad())
  val dip = genPAD("dip", soc.dip)(HPad())
  val hex7seg = genPAD("hex7seg", soc.hex7seg)(HPad())
  val customOut = genPAD("customOut", soc.customOut)(HPad())
  val customIn = genPAD("customIn", soc.customIn)(HPad())
}
