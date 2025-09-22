package ysyx

import chisel3._
import chisel3.util._

import freechips.rocketchip.amba.apb._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util._

class UARTIO extends Bundle {
  val rx = Input(Bool())
  val tx = Output(Bool())
}

class uart_top_apb extends BlackBox {
  val io = IO(new Bundle {
    val clock = Input(Clock())
    val reset = Input(Reset())
    val in = Flipped(new APBBundle(APBBundleParameters(addrBits = 32, dataBits = 32)))
    val uart = new UARTIO
  })
}

class APBUart16550(address: Seq[AddressSet])(implicit p: Parameters)
  extends APB4DevTemplate(address, new UARTIO)((in: APBBundle, outer: LazyModuleImp, irq_o: Bool, extra) => {
  val muart = Module(new uart_top_apb)
  muart.io.clock := outer.clock
  muart.io.reset := outer.reset
  muart.io.in <> in
  extra <> muart.io.uart
})
