package ysyx

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.subsystem._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util._

class IMEM extends Bundle {
  val addr = Output(UInt(32.W))
  val reqValid = Output(Bool())
  val rdata = Input(UInt(32.W))
  val respValid = Input(Bool())
}

class DMEM extends IMEM {
  val size = Output(UInt(2.W))
  val wen = Output(Bool())
  val wdata = Output(UInt(32.W))
  val wmask = Output(UInt(4.W))
}

object Lookup1H {
  def mkmap[T <: Data, U <: Data](key: T, mapping: Iterable[(T, U)]) = mapping.map(p => (p._1 === key, p._2))
  def apply[T <: Data, U <: Data](key: T, mapping: Iterable[(T, U)]): U = Mux1H(mkmap(key, mapping))
  def apply[T <: UInt, U <: Data](key: T, default: U, mapping: Iterable[(T, U)]): U =
    Mux1H(mkmap(key, mapping) ++ Iterable((mapping.map(p => (p._1 =/= key)).reduce(_ && _), default)))
  def apply[T <: UInt](default: T, mapping: Iterable[(Bool, T)]): T =
    Mux1H(mapping ++ Iterable((mapping.map(!_._1).reduce(_ && _), default)))
  def apply[T <: UInt](mapping: Iterable[(Bool, T)]): T = Mux1H(mapping)
}

class MemBridge extends Module { // only adapt to multi-cycle CPU
  val io = IO(new Bundle {
    val ifu = Flipped(new IMEM)
    val lsu = Flipped(new DMEM)
    val master = AXI4Bundle(CPUAXI4BundleParameters())
  })

  val out = io.master
  val isValidLoad  = io.lsu.reqValid && !io.lsu.wen
  val isValidStore = io.lsu.reqValid &&  io.lsu.wen

  object IMEMState extends ChiselEnum {
    val idle, waitARready, waitRvalid = Value
  }

  object DMEMState extends ChiselEnum {
    val idle, waitARready, waitRvalid, waitAWready, waitWready, waitAWready2, waitBvalid = Value
  }

  val stateI = RegInit(IMEMState.idle)
  stateI := Lookup1H(stateI, Seq(
    IMEMState.idle        -> Mux(io.ifu.reqValid, Mux(out.ar.ready, IMEMState.waitRvalid, IMEMState.waitARready),
                                IMEMState.idle),
    IMEMState.waitARready -> Mux(out.ar.ready, IMEMState.waitRvalid,  IMEMState.waitARready),
    IMEMState.waitRvalid  -> Mux(out. r.valid, IMEMState.idle,        IMEMState.waitRvalid),
  ))

  val stateD = RegInit(DMEMState.idle)
  stateD := Lookup1H(stateD, Seq(
    DMEMState.idle        -> Mux(isValidLoad, Mux(out.ar.ready, DMEMState.waitRvalid, DMEMState.waitARready),
                               Mux(isValidStore,
                                 Mux(out.aw.ready,
                                   Mux(out.w.ready, DMEMState.waitBvalid, DMEMState.waitWready),
                                   Mux(out.w.ready, DMEMState.waitAWready2, DMEMState.waitAWready)),
                                DMEMState.idle)),
    DMEMState.waitARready -> Mux(out.ar.ready, DMEMState.waitRvalid, DMEMState.waitARready),
    DMEMState.waitRvalid  -> Mux(out. r.valid, DMEMState.idle,       DMEMState.waitRvalid),
    DMEMState.waitAWready -> Mux(out.aw.ready,
                               Mux(out.w.ready, DMEMState.waitBvalid, DMEMState.waitWready),
                               DMEMState.waitAWready),
    DMEMState.waitAWready2-> Mux(out.aw.ready, DMEMState.waitBvalid, DMEMState.waitAWready2),
    DMEMState.waitWready  -> Mux(out. w.ready, DMEMState.waitBvalid, DMEMState.waitWready),
    DMEMState.waitBvalid  -> Mux(out. b.valid, DMEMState.idle,       DMEMState.waitBvalid),
  ))

  val lsuRead = ((stateD === DMEMState.idle) && isValidLoad) || (stateD === DMEMState.waitARready)
  out.ar.valid := ((stateI === IMEMState.idle) && io.ifu.reqValid) || (stateI === IMEMState.waitARready) || lsuRead
  out.ar.bits := DontCare
  out.ar.bits.addr := Mux(lsuRead, io.lsu.addr, io.ifu.addr)
  out.ar.bits.size := Mux(lsuRead, io.lsu.size, "b010".U)
  out.ar.bits.id := 0.U
  out.ar.bits.len := 0.U
  out.ar.bits.burst := 0.U
  out. r.ready := (stateI === IMEMState.waitRvalid) || (stateD === DMEMState.waitRvalid)

  out.aw.valid := ((stateD === DMEMState.idle) && isValidStore) || stateD.isOneOf(DMEMState.waitAWready, DMEMState.waitAWready2)
  out.aw.bits := out.ar.bits
  out.aw.bits.addr := io.lsu.addr
  out.aw.bits.size := io.lsu.size
  out. w.valid := ((stateD === DMEMState.idle) && isValidStore) || (stateD === DMEMState.waitWready)
  out. w.bits.data := io.lsu.wdata
  out. w.bits.strb := io.lsu.wmask
  out. w.bits.last := true.B
  out. b.ready := (stateD === DMEMState.waitBvalid)

  val instReturn = out.r.valid && (stateI === IMEMState.waitRvalid)
  io.ifu.rdata := Mux(instReturn, out.r.bits.data, RegEnable(out.r.bits.data, instReturn)) // keep inst unchanged here
  io.ifu.respValid := instReturn

  io.lsu.rdata := out.r.bits.data
  io.lsu.respValid := ((stateD === DMEMState.waitRvalid) && out.r.valid) ||
                      ((stateD === DMEMState.waitBvalid) && out.b.valid)
}
