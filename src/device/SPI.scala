package ysyx

import chisel3._
import chisel3.util._

import freechips.rocketchip.amba.apb._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util._

class SPIIO(nss: Int = 1) extends Bundle {
  val sck = Output(Bool())
  val ss = Output(UInt(nss.W))
  val mosi = Output(Bool())
  val miso = Input(Bool())
}

class spi_top_apb extends BlackBox {
  val io = IO(new Bundle {
    val clock = Input(Clock())
    val reset = Input(Reset())
    val in = Flipped(new APBBundle(APBBundleParameters(addrBits = 32, dataBits = 32)))
    val spi = new SPIIO(8)
    val spi_irq_out = Output(Bool())
  })
}

class flash extends BlackBox {
  val io = IO(Flipped(new SPIIO(1)))
}

class APBSPI(address: Seq[AddressSet], nss: Int = 1)(implicit p: Parameters)
  extends APB4DevTemplate(address, new SPIIO(nss))((in: APBBundle, outer: LazyModuleImp, irq_o: Bool, extra) => {
  val mspi = Module(new spi_top_apb)
  mspi.io.clock := outer.clock
  mspi.io.reset := outer.reset
  mspi.io.in <> in
  extra <> mspi.io.spi
})
