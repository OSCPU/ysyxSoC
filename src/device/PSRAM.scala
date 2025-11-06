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

  // PSRAM controller has 4 chips, each 8MB (23-bit address + 2-bit chip select).
  // Total addressable space: 32MB (0x0000_0000 ~ 0x01FF_FFFF after offset removal)
  // Map input address range to PSRAM controller's expected format:
  //   - Bits [31:28]: 0x4 (controller identification)
  //   - Bits [27:25]: zero padding
  //   - Bits [24:23]: chip select (handled by psram.sv)
  //   - Bits [22:2]:  address within chip
  //   - Bits [1:0]:   always 0 (word-aligned)
  val cs:   UInt = io.in.paddr(24, 23)
  val addr: UInt = io.in.paddr(22, 2)
  val remappedAddress: UInt = Cat("h4".U(4.W), 0.U(3.W), cs, addr, 0.U(2.W))  // 32 bits total

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
  extends APB4DevTemplate(address, new PSRAMQSPIBundle)((in: APBBundle, outer: LazyModuleImp, irq_o: Bool, extra) => {
  // Check if the address set has only one element and get the base address
  require(address.length == 1, "APBPSRAM requires only one address set now")
  val mpsram = Module(new PSRAMWrapper(address.head.base))
  mpsram.clock := outer.clock
  mpsram.reset := outer.reset
  mpsram.io.in <> in
  extra <> mpsram.io.qspi
})
