package ysyx

import chisel3._
import chisel3.util._
import chisel3.experimental.Analog
import chisel3.reflect.DataMirror // 导入关键的反射API

// for clock
class tc_io_xtl_pad extends BlackBox {
  val io = IO(new Bundle {
    val xi_pad = Input(Clock())
    val xo_pad = Output(Clock())
    val en = Input(Bool())
    val clk = Output(Clock())
  })
}

// for data
class tc_io_in_pad extends BlackBox {
  val io = IO(new Bundle {
    val pad = Input(Bool())
    val p2c = Output(Bool())
  })
}

class tc_io_out_pad extends BlackBox {
  val io = IO(new Bundle {
    val pad = Output(Bool())
    val c2p = Input(Bool())
  })
}

class tc_io_tri_pad extends BlackBox {
  val io = IO(new Bundle {
    val pad = Output(Bool())
    val c2p = Input(Bool())  // chip to pad
    val c2p_en = Input(Bool())
    val p2c = Output(Bool())
  })
}

object GenPAD {
  def input(port: Bool) = {
    val pad = Module(new tc_io_in_pad)
    pad.io.pad := port
    pad.io.p2c
  }
  def output(internal: Bool) = {
    val pad = Module(new tc_io_out_pad)
    pad.io.c2p := internal
    pad.io.pad
  }
  def clock(clkIn: Clock) = {
    val pad = Module(new tc_io_xtl_pad)
    pad.io.en := true.B
    pad.io.xi_pad := clkIn
    //pad.io.xo_pad // don't care now
    pad.io.clk
  }
  def apply[T <: Data](port: T, internal: T): Unit = {
    (port, internal) match {
      case (p: Bool, i: Bool) =>
        DataMirror.specifiedDirectionOf(p) match {
          case SpecifiedDirection.Input  => i := input(p)
          case SpecifiedDirection.Output => p := output(i)
          case _ => require(false, "unsupport direction")
        }
      case (p: UInt, i: UInt) =>
        require(p.getWidth == i.getWidth)
        DataMirror.specifiedDirectionOf(p) match {
          case SpecifiedDirection.Input  => i := Cat(p.asBools.map(input(_)).reverse)
          case SpecifiedDirection.Output => p := Cat(i.asBools.map(output(_)).reverse)
          case _ => require(false, "unsupport direction")
        }
      case (p: Reset, i: Reset) => i := input(p.asBool)
      case (p: Clock, i: Clock) => i := clock(p)
      case (p: Analog, i: Analog) => require(false)
      case (p: Record, i: Record) =>
        (p.elements zip i.elements).map { case (p0, i0) => apply(p0._2, i0._2) }
      case (p: Vec[_], i: Vec[_]) =>
        (p zip i).map { case (p0, i0) => apply(p0, i0) }
    }
  }
}
