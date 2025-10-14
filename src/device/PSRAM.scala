package ysyx

import chisel3._
import chisel3.util._
import chisel3.experimental.Analog

import freechips.rocketchip.amba.apb._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util._

class ESP_PSRAM64H extends BlackBox {
  val io = IO(new Bundle {
    val sclk: Clock  = Input(Clock())
    val csn:  Bool   = Input(Bool())
    val sio:  Analog = Analog(4.W)
  })
}

class ESPWrapper extends RawModule {
  val io:       PSRAMQSPIBundle = IO(Flipped(new PSRAMQSPIBundle))
  val espPsram: ESP_PSRAM64H    = Module(new ESP_PSRAM64H)

  espPsram.io.sclk := io.sck_o.asClock
  espPsram.io.csn  := io.nss_o(0)
  io.io_di_i   := TriStateInBuf(espPsram.io.sio, io.io_do_o, io.io_oe_o.orR)
}

class NmiIO extends Bundle {
  val valid: Bool = Input(Bool())
  val addr:  UInt = Input(UInt(32.W))
  val wdata: UInt = Input(UInt(32.W))
  val wstrb: UInt = Input(UInt(4.W))
  val rdata: UInt = Output(UInt(32.W))
  val ready: Bool = Output(Bool())
}

class PSRAMQSPIBundle(nss: Int = 4) extends Bundle {
  val sck_o:    Bool = Output(Bool())
  val nss_o:    UInt = Output(UInt(nss.W))
  val io_oe_o:  UInt = Output(UInt(4.W))
  val io_di_i:  UInt = Input(UInt(4.W))
  val io_do_o:  UInt = Output(UInt(4.W))
  val irq_o:    Bool = Output(Bool())
}

class nmi_psram extends BlackBox {
  val io = IO(new Bundle {
    val clk_i:   Clock           = Input(Clock())
    val rst_n_i: Bool            = Input(Bool())
    val nmi:     NmiIO           = new NmiIO
    val psram:   PSRAMQSPIBundle = new PSRAMQSPIBundle
  })
}

class PSRAMWrapper(address: BigInt) extends Module {
  val io           = IO(new Bundle {
    val in:   APBBundle       = Flipped(new APBBundle(APBBundleParameters(addrBits = 32, dataBits = 32)))
    val qspi: PSRAMQSPIBundle = new PSRAMQSPIBundle
  })
  val npsram: nmi_psram = Module(new nmi_psram)

  val addrReg:  UInt = Reg(UInt(32.W))
  val wdataReg: UInt = Reg(UInt(32.W))
  val wstrbReg: UInt = Reg(UInt(4.W))

  // The nmi_psram will check the upper 8-bit to be 0x40 or 0x41
  // so we need to remap the address.
  val addressOffset:   UInt = io.in.paddr - address.U
  val addressAligned:  UInt = Cat(addressOffset(31, 2), 0.U(2.W))
  val remappedAddress: UInt = Cat("h40".U(8.W), addressAligned(23, 0)) //TODO: 0x41 as well?

  val psram_idle :: psram_active :: Nil = Enum(2)
  val psram_state: UInt = RegInit(psram_idle)

  val setup: Bool = io.in.psel && !io.in.penable

  switch(psram_state) {
    is(psram_idle) {
      psram_state := Mux(setup, psram_active, psram_idle)
      addrReg     := Mux(setup, remappedAddress, addrReg)
      wdataReg    := Mux(setup, io.in.pwdata, wdataReg)
      wstrbReg    := Mux(setup, Mux(io.in.pwrite, io.in.pstrb, 0.U), wstrbReg)
    }
    is(psram_active) {
      psram_state := Mux(npsram.io.nmi.ready, psram_idle, psram_active)
    }
  }
  val active: Bool = psram_state === psram_active

  npsram.io.clk_i     := clock
  npsram.io.rst_n_i   := !reset.asBool
  npsram.io.nmi.valid := active
  npsram.io.nmi.addr  := addrReg
  npsram.io.nmi.wdata := wdataReg
  npsram.io.nmi.wstrb := wstrbReg
  io.in.prdata        := npsram.io.nmi.rdata
  io.in.pready        := !active || npsram.io.nmi.ready
  io.in.pslverr       := false.B
  io.qspi <> npsram.io.psram
}

class APBPSRAM(address: Seq[AddressSet])(implicit p: Parameters)
  extends APB4DevTemplate(address, new PSRAMQSPIBundle)((in: APBBundle, outer: LazyModuleImp, extra) => {
  // Check if the address set has only one element and get the base address
  require(address.length == 1, "APBPSRAM requires only one address set now")
  val mpsram = Module(new PSRAMWrapper(address.head.base))
  mpsram.clock := outer.clock
  mpsram.reset := outer.reset
  mpsram.io.in <> in
  extra <> mpsram.io.qspi
})
