package freechips.rocketchip.system

import chisel3._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy.LazyModule
import ysyx._

class TestHarness()(implicit p: Parameters) extends Module {
  val io = IO(new Bundle { })
  val ldut = LazyModule(new ysyxSoCFull)
  val dut = Module(ldut.module)
  dut.dontTouchPorts()
  dut.cpu_master := DontCare
  dut.cpu_slave := DontCare
  dut.uart.rx := true.B
  dut.spi.miso := true.B
}
