package ysyx

import chisel3._
import chisel3.util._
import chisel3.util.experimental.forceName

import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.subsystem._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util._

object CPUAXI4BundleParameters {
  def apply() = AXI4BundleParameters(addrBits = 32, dataBits = 32, idBits = Config.idBits)
}

class CPUBundle extends Bundle {
  val interrupt = if (!Config.isMini) Some(Input(Bool())) else None
  val master = if (!Config.isMini) Some(AXI4Bundle(CPUAXI4BundleParameters())) else None
  val slave = if (!Config.isMini) Some(Flipped(AXI4Bundle(CPUAXI4BundleParameters()))) else None
  val ifu = if (Config.isMini) Some(new IMEM) else None
  val lsu = if (Config.isMini) Some(new DMEM) else None
}

class ysyx_00000000 extends BlackBox {
  val io = IO(new Bundle {
    val clock = Input(Clock())
    val reset = Input(Reset())
    val io = new CPUBundle
  })
}

class CoreMultiplexer(numCore: Int) extends Module {
  val io = IO(new Bundle {
    val coreSel = Input(UInt(Config.coreSelWidth.W))
    val out = AXI4Bundle(CPUAXI4BundleParameters())
    val in = Flipped(Vec(numCore, AXI4Bundle(CPUAXI4BundleParameters())))
    val interrupt_in = Input(Bool())
    val interrupt_out = Output(Vec(numCore, Bool()))
  })

  io.out := DontCare
  io.in.zipWithIndex.map { case (in, i) =>
    in := DontCare
    when (i.U === io.coreSel) {
      io.out <> in
    }
  }

  io.interrupt_out := 0.U.asTypeOf(io.interrupt_out)
  io.interrupt_out(io.coreSel) := io.interrupt_in
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

    val cmp = Module(new CoreMultiplexer(Config.numCore))
    val io_coreSel = IO(Input(UInt(cmp.io.coreSel.getWidth.W)))
    cmp.io.coreSel := io_coreSel

    cmp.io.interrupt_in := interrupt

    master <> cmp.io.out
    cmp.io.in.zipWithIndex.map { case (in, i) =>
      val cpu = Module(new ysyx_00000000)
      forceName(cpu, s"core${i}")
      cpu.io.clock := clock
      cpu.io.reset := reset
      in <> (if (Config.isMini) {
        val bridge = Module(new MemBridge)
        forceName(bridge, s"bridge${i}")
        bridge.io.ifu <> cpu.io.io.ifu.get
        bridge.io.lsu <> cpu.io.io.lsu.get
        slave := DontCare
        bridge.io.master
      } else {
        cpu.io.io.interrupt.get := cmp.io.interrupt_out(i)
        cpu.io.io.slave.get <> slave  // FIXME: this should also use CoreMultiplexer
        cpu.io.io.master.get
      })
    }
  }
}
