package ysyx

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.subsystem._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util._

object CPUAXI4BundleParameters {
  def apply() = AXI4BundleParameters(addrBits = 32, dataBits = 32, idBits = Config.idBits)
}

class ysyx_00000000 extends BlackBox {
  val io = IO(new Bundle {
    val clock = Input(Clock())
    val reset = Input(Reset())
    val io_interrupt = if (!Config.isMini) Some(Input(Bool())) else None
    val io_master = if (!Config.isMini) Some(AXI4Bundle(CPUAXI4BundleParameters())) else None
    val io_slave = if (!Config.isMini) Some(Flipped(AXI4Bundle(CPUAXI4BundleParameters()))) else None
    val io_ifu = if (Config.isMini) Some(new IMEM) else None
    val io_lsu = if (Config.isMini) Some(new DMEM) else None
  })
}

class CPU(idBits: Int)(implicit p: Parameters) extends LazyModule {
  val masterNode = AXI4MasterNode(p(ExtIn).map(params =>
    AXI4MasterPortParameters(
      masters = Seq(AXI4MasterParameters(
        name = "cpu",
        id   = IdRange(0, 1 << idBits))))).toSeq)
  lazy val module = new Impl
  class Impl extends LazyModuleImp(this) {
    val (master, _) = masterNode.out(0)
    val interrupt = IO(Input(Bool()))
    val slave = IO(Flipped(AXI4Bundle(CPUAXI4BundleParameters())))

    val cpu = Module(new ysyx_00000000)
    cpu.io.clock := clock
    cpu.io.reset := reset
    if (Config.isMini) {
      val bridge = Module(new MemBridge)
      bridge.io.ifu <> cpu.io.io_ifu.get
      bridge.io.lsu <> cpu.io.io_lsu.get
      master <> bridge.io.master
      slave := DontCare
    }
    else {
      cpu.io.io_interrupt.get := interrupt
      cpu.io.io_slave.get <> slave
      master <> cpu.io.io_master.get
    }
  }
}
