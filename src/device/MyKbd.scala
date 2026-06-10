package ysyx

import chisel3._
import chisel3.util._

import freechips.rocketchip.amba.apb._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util._

class MyKbdIO extends Bundle {
  val clk = Input(Bool())
  val data = Input(Bool())
}

class MyKbdCtrlIO extends Bundle {
  val clock = Input(Clock())
  val reset = Input(Bool())
  val in = Flipped(new APBBundle(APBBundleParameters(addrBits = 32, dataBits = 32)))
  val kbd = new MyKbdIO
}

class mykbd_top_apb extends BlackBox {
  val io = IO(new MyKbdCtrlIO)
}

class mykbdChisel extends Module {
  val io = IO(new MyKbdCtrlIO)
}

class APB4MyKbd(address: Seq[AddressSet])(implicit p: Parameters)
  extends APB4DevTemplate(address, new MyKbdIO)((in: APBBundle, outer: LazyModuleImp, irq_o: Bool, extra) => {
  val mkbd = Module(new mykbd_top_apb)
  mkbd.io.clock := outer.clock
  mkbd.io.reset := outer.reset
  mkbd.io.in <> in
  extra <> mkbd.io.kbd
})
