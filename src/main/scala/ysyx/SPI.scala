package ysyx

import chisel3._
import chisel3.util._

import freechips.rocketchip.amba.apb._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util._

class SPIIO(val csWidth: Int = 2) extends Bundle {
  val clk = Output(Bool())
  val cs = Output(UInt(csWidth.W))
  val mosi = Output(Bool())
  val miso = Input(Bool())
}

class spi extends BlackBox {
  val io = IO(new Bundle {
    val clk = Input(Clock())
    val resetn = Input(Bool())
    val in = Flipped(new APBBundle(APBBundleParameters(addrBits = 32, dataBits = 32)))
    val spi = new SPIIO
    val spi_irq_out = Output(Bool())
  })
}

class spiFlash extends BlackBox {
  val io = IO(Flipped(new SPIIO))
}

class APBSPI(address: Seq[AddressSet])(implicit p: Parameters) extends LazyModule {
  val node = APBSlaveNode(Seq(APBSlavePortParameters(
    Seq(APBSlaveParameters(
      address       = address,
      executable    = true,
      supportsRead  = true,
      supportsWrite = true)),
    beatBytes  = 4)))

  private val outer = this

  lazy val module = new LazyModuleImp(this) {
    val (in, _) = node.in(0)
    val spi_bundle = IO(new SPIIO)

    val mspi = Module(new spi)
    mspi.io.clk := clock
    mspi.io.resetn := ~reset.asBool
    mspi.io.in <> in
    spi_bundle <> mspi.io.spi
  }
}
