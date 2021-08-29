package ysyx

import chisel3._
import chisel3.util._

import freechips.rocketchip.amba.apb._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util._

class UARTIO extends Bundle {
  val rx = Input(Bool())
  val tx = Output(Bool())
}

class uart_apb extends BlackBox {
  val io = IO(new Bundle {
    val clk = Input(Clock())
    val resetn = Input(Bool())
    val in = Flipped(new APBBundle(APBBundleParameters(addrBits = 32, dataBits = 32)))
    val uart = new UARTIO
  })
}

class APBUart16550(address: Seq[AddressSet])(implicit p: Parameters) extends LazyModule {
  val node = APBSlaveNode(Seq(APBSlavePortParameters(
    Seq(APBSlaveParameters(
      address       = address,
      executable    = false,
      supportsRead  = true,
      supportsWrite = true)),
    beatBytes  = 4)))

  private val outer = this

  lazy val module = new LazyModuleImp(this) {
    val (in, _) = node.in(0)
    val uart = IO(new UARTIO)

    val muart = Module(new uart_apb)
    muart.io.clk := clock
    muart.io.resetn := ~reset.asBool
    muart.io.in <> in
    uart <> muart.io.uart
  }
}
