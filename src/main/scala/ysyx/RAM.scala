package ysyx

import chisel3._
import chisel3.util._

import freechips.rocketchip.config.Parameters
import freechips.rocketchip.subsystem._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util._

class RAMIO extends Bundle {
  val addr  = Input(UInt(6.W))
  val cen   = Input(Bool())
  val wen   = Input(Bool())
  val wmask = Input(UInt(128.W))
  val wdata = Input(UInt(128.W))
  val rdata = Output(UInt(128.W))
}

class S011HD1P_X32Y2D128_BW extends BlackBox {
    val io = IO(new Bundle {
        val Q    = Output(UInt(128.W))
        val CLK  = Input(Clock())
        val CEN  = Input(Bool())
        val WEN  = Input(Bool())
        val BWEN = Input(UInt(128.W))
        val A    = Input(UInt(6.W))
        val D    = Input(UInt(128.W))
    })
}

class RAM(implicit p: Parameters) extends LazyModule {
    lazy val module = new LazyModuleImp(this) {
        val io = IO(new RAMIO)
        val ram = Module(new S011HD1P_X32Y2D128_BW)
        io.rdata    := ram.io.Q
        ram.io.CLK  := clock
        ram.io.CEN  := io.cen
        ram.io.WEN  := io.wen
        ram.io.BWEN := io.wmask
        ram.io.A    := io.addr
        ram.io.D    := io.wdata
    }
}