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
  dut.cpu_mem := DontCare
  dut.cpu_mmio.foreach(_ := DontCare)
  dut.cpu_dma.foreach(_ := DontCare)
  dut.spi.foreach(_ := DontCare)
  dut.uart.foreach(_ := DontCare)
}
