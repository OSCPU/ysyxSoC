package ysyx

import chisel3._

import freechips.rocketchip.config.{Parameters}
import freechips.rocketchip.subsystem._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.diplomacy._

object CPUAXI4BundleParameters {
  def apply() = AXI4BundleParameters(addrBits = 32, dataBits = 64, idBits = ChipLinkParam.idBits)
}

class ysyx_000000 extends BlackBox {
  val io = IO(new Bundle {
    val clock = Input(Clock())
    val reset = Input(Reset())
    val io_interrupt = Input(Bool())
    val io_master = AXI4Bundle(CPUAXI4BundleParameters())
    val io_slave = Flipped(AXI4Bundle(CPUAXI4BundleParameters()))
    val io_sram0 = Flipped(new RAMIO)
    val io_sram1 = Flipped(new RAMIO)
    val io_sram2 = Flipped(new RAMIO)
    val io_sram3 = Flipped(new RAMIO)
    val io_sram4 = Flipped(new RAMIO)
    val io_sram5 = Flipped(new RAMIO)
    val io_sram6 = Flipped(new RAMIO)
    val io_sram7 = Flipped(new RAMIO)
  })
}

class CPU(idBits: Int)(implicit p: Parameters) extends LazyModule {
  val masterNode = AXI4MasterNode(p(ExtIn).map(params =>
    AXI4MasterPortParameters(
      masters = Seq(AXI4MasterParameters(
        name = "cpu",
        id   = IdRange(0, 1 << idBits))))).toSeq)
  lazy val module = new LazyModuleImp(this) {
    val (master, _) = masterNode.out(0)
    val interrupt = IO(Input(Bool()))
    val slave = IO(Flipped(AXI4Bundle(CPUAXI4BundleParameters())))
    val sram0 = IO(Flipped(new RAMIO))
    val sram1 = IO(Flipped(new RAMIO))
    val sram2 = IO(Flipped(new RAMIO))
    val sram3 = IO(Flipped(new RAMIO))
    val sram4 = IO(Flipped(new RAMIO))
    val sram5 = IO(Flipped(new RAMIO))
    val sram6 = IO(Flipped(new RAMIO))
    val sram7 = IO(Flipped(new RAMIO))

    val cpu = Module(new ysyx_000000)

    cpu.io.clock := clock
    cpu.io.reset := reset
    cpu.io.io_interrupt := interrupt
    cpu.io.io_slave <> slave
    master <> cpu.io.io_master
    cpu.io.io_sram0 <> sram0
    cpu.io.io_sram1 <> sram1
    cpu.io.io_sram2 <> sram2
    cpu.io.io_sram3 <> sram3
    cpu.io.io_sram4 <> sram4
    cpu.io.io_sram5 <> sram5
    cpu.io.io_sram6 <> sram6
    cpu.io.io_sram7 <> sram7
  }
}
