package ysyx

import chisel3._
import chisel3.util._
import chisel3.experimental.Analog

import freechips.rocketchip.amba.apb._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util._

class QSPIIO extends Bundle {
  val sck = Output(Bool())
  val ce_n = Output(Bool())
  val dio = Analog(4.W)
}

class psram_top_apb extends BlackBox {
  val io = IO(new Bundle {
    val clock = Input(Clock())
    val reset = Input(Reset())
    val in = Flipped(new APBBundle(APBBundleParameters(addrBits = 32, dataBits = 32)))
    val qspi = new QSPIIO
  })
}

class psram extends BlackBox {
  val io = IO(Flipped(new QSPIIO))
}

class psramChisel extends RawModule {
  val io = IO(Flipped(new QSPIIO))
  val di = TriStateInBuf(io.dio, 0.U, false.B) // change this if you need
}

class APBPSRAM(address: Seq[AddressSet])(implicit p: Parameters)
  extends APB4DevTemplate(address, new QSPIIO)((in: APBBundle, outer: LazyModuleImp, extra) => {
  val mpsram = Module(new psram_top_apb)
  mpsram.io.clock := outer.clock
  mpsram.io.reset := outer.reset
  mpsram.io.in <> in
  extra <> mpsram.io.qspi
})
