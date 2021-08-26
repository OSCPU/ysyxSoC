package freechips.rocketchip.system

import chisel3._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy.LazyModule
import ysyx._

class TestHarness()(implicit p: Parameters) extends Module {
  val io = IO(new Bundle { })
  val ldut = LazyModule(new ChipLinkMaster)
  val dut = Module(ldut.module)
  dut.dontTouchPorts()
  dut.slave_mem := DontCare
  dut.master_mmio.foreach(_ := DontCare)
  dut.master_mem.foreach(_(0) := DontCare)
  dut.fpga_io.b2c := DontCare
}
