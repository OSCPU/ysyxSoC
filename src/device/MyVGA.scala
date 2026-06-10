package ysyx

import chisel3._
import chisel3.util._

import freechips.rocketchip.amba.apb._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util._

class MyVGAIO extends Bundle {
  val r = Output(UInt(8.W))
  val g = Output(UInt(8.W))
  val b = Output(UInt(8.W))
  val hsync = Output(Bool())
  val vsync = Output(Bool())
  val valid = Output(Bool())
}

class MyVGACtrlIO extends Bundle {
  val clock = Input(Clock())
  val reset = Input(Bool())
  val in = Flipped(new APBBundle(APBBundleParameters(addrBits = 32, dataBits = 32)))
  val vga = new MyVGAIO
}

class myvga_top_apb extends BlackBox {
  val io = IO(new MyVGACtrlIO)
}

class myvgaChisel extends Module {
  val io = IO(new MyVGACtrlIO)
}

class APB4MyVGA(address: Seq[AddressSet])(implicit p: Parameters)
  extends APB4DevTemplate(address, new MyVGAIO)((in: APBBundle, outer: LazyModuleImp, irq_o: Bool, extra) => {
  val mvga = Module(new myvga_top_apb)
  mvga.io.clock := outer.clock
  mvga.io.reset := outer.reset
  mvga.io.in <> in
  extra <> mvga.io.vga
})
