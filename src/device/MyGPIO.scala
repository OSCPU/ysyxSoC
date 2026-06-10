package ysyx

import chisel3._
import chisel3.util._

import freechips.rocketchip.amba.apb._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util._

class MyGPIOIO extends Bundle {
  val out = Output(UInt(16.W))
  val in = Input(UInt(16.W))
  val seg = Output(Vec(8, UInt(8.W)))
}

class MyGPIOCtrlIO extends Bundle {
  val clock = Input(Clock())
  val reset = Input(Reset())
  val in = Flipped(new APBBundle(APBBundleParameters(addrBits = 32, dataBits = 32)))
  val gpio = new MyGPIOIO
}

class mygpio_top_apb extends BlackBox {
  val io = IO(new MyGPIOCtrlIO)
}

class mygpioChisel extends Module {
  val io = IO(new MyGPIOCtrlIO)
}

class APB4MyGPIO(address: Seq[AddressSet])(implicit p: Parameters)
  extends APB4DevTemplate(address, new MyGPIOIO)((in: APBBundle, outer: LazyModuleImp, irq_o: Bool, extra) => {
  val mgpio = Module(new mygpio_top_apb)
  mgpio.io.clock := outer.clock
  mgpio.io.reset := outer.reset
  mgpio.io.in <> in
  extra <> mgpio.io.gpio
})
