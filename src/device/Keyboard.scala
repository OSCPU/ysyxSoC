package ysyx

import chisel3._
import chisel3.util._

import freechips.rocketchip.amba.apb._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util._

class PS2IO extends Bundle {
  val clk = Input(Bool())
  val data = Input(Bool())
}

class PS2CtrlIO extends Bundle {
  val clock = Input(Clock())
  val reset = Input(Bool())
  val in = Flipped(new APBBundle(APBBundleParameters(addrBits = 32, dataBits = 32)))
  val ps2 = new PS2IO
}

class ps2_top_apb extends BlackBox {
  val io = IO(new PS2CtrlIO)
}

class ps2Chisel extends Module {
  val io = IO(new PS2CtrlIO)
}

class APBKeyboard(address: Seq[AddressSet])(implicit p: Parameters)
  extends APB4DevTemplate(address, new PS2IO)((in: APBBundle, outer: LazyModuleImp, irq_o: Bool, extra) => {
  val mps2 = Module(new ps2_top_apb)
  mps2.io.clock := outer.clock
  mps2.io.reset := outer.reset
  mps2.io.in <> in
  extra <> mps2.io.ps2
})
