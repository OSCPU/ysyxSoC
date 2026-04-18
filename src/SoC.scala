package ysyx

import chisel3._
import chisel3.util._
import chisel3.experimental.Analog

import freechips.rocketchip.diplomacy._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.subsystem._
import freechips.rocketchip.util._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.amba.apb._
import freechips.rocketchip.system.SimAXIMem

object AXI4SlaveNodeGenerator {
  def apply(params: Option[MasterPortParams], address: Seq[AddressSet])(implicit valName: ValName) =
    AXI4SlaveNode(params.map(p => AXI4SlavePortParameters(
        slaves = Seq(AXI4SlaveParameters(
          address       = address,
          executable    = p.executable,
          supportsWrite = TransferSizes(1, p.maxXferBytes),
          supportsRead  = TransferSizes(1, p.maxXferBytes))),
        beatBytes = p.beatBytes
      )).toSeq)
}

class ysyxSoC(implicit p: Parameters) extends LazyModule {
  val xbar = AXI4Xbar()
  val apbxbar = LazyModule(new APBFanout).node
  val cpu = LazyModule(new CPU(idBits = Config.idBits))

  val isMini = Config.isMini
  def AddrSpace(base: BigInt, len: BigInt = 0x1000) = AddressSet.misaligned(base, len)
  def DefDevice[T <: LazyModule](dev: () => T, cond: Boolean = true) = {
    if (cond) Some(LazyModule(dev())) else None
  }

  // RISC-V system
  val lclint    = DefDevice(() => new APB4CLINT   (AddrSpace(0x02010000, 0x10000)), !isMini)
  val lplic     = DefDevice(() => new APB4PLIC    (AddrSpace(0x0c000000, 0x40)), !isMini)

  // generic system
  val luart0    = DefDevice(() => new APBUart16550(AddrSpace(0x10000000, 0x8)))
  val lspi      = DefDevice(() => new APBSPI      (AddrSpace(0x10001000, 0x20)   ++     // SPI controller
                                                   AddrSpace(0x30000000, 0x10000000)))  // XIP flash
  val lrcu      = DefDevice(() => new APB4RCU     (AddrSpace(0x10002000, 0x1000)), !isMini)
  val lrtc      = DefDevice(() => new APB4RTC     (AddrSpace(0x10004000, 0x20)), !isMini)
  val lwdg      = DefDevice(() => new APB4WDG     (AddrSpace(0x10005000, 0x20)), !isMini)
  val larchinfo = DefDevice(() => new APB4ArchInfo(AddrSpace(0x10006000, 0x10)), !isMini)

  // interface
  val lgpio0    = DefDevice(() => new APB4GPIO    (AddrSpace(0x10100000, 0x40)), !isMini)
  val lgpio1    = DefDevice(() => new APB4GPIO    (AddrSpace(0x10101000, 0x40)), !isMini)
  val lgpio2    = DefDevice(() => new APB4GPIO    (AddrSpace(0x10102000, 0x40)), !isMini)
  val luart1    = DefDevice(() => new APB4UART    (AddrSpace(0x10103000, 0x20)), !isMini)
  val li2c      = DefDevice(() => new APB4I2C     (AddrSpace(0x10104000, 0x20)), !isMini)
  val lps2      = DefDevice(() => new APB4PS2     (AddrSpace(0x10105000, 0x10)), !isMini)
  val lpwm0     = DefDevice(() => new APB4PWM     (AddrSpace(0x10106000, 0x40)), !isMini)
  val lpwm1     = DefDevice(() => new APB4PWM     (AddrSpace(0x10107000, 0x40)), !isMini)
  val ltim0     = DefDevice(() => new APB4Timer   (AddrSpace(0x10108000, 0x20)), !isMini)
  val ltim1     = DefDevice(() => new APB4Timer   (AddrSpace(0x10109000, 0x20)), !isMini)
  val ltim2     = DefDevice(() => new APB4Timer   (AddrSpace(0x1010a000, 0x20)), !isMini)
  val ltim3     = DefDevice(() => new APB4Timer   (AddrSpace(0x1010b000, 0x20)), !isMini)

  // multimedia
  val lqspi     = DefDevice(() => new APB4QSPI    (AddrSpace(0x10200000, 0x20)), !isMini)
  val li2s      = DefDevice(() => new APB4I2S     (AddrSpace(0x10201000, 0x20)), !isMini)

  // application
  val lrng      = DefDevice(() => new APB4RNG     (AddrSpace(0x10300000, 0x10)), !isMini)
  val lcrc      = DefDevice(() => new APB4CRC     (AddrSpace(0x10301000, 0x20)), !isMini)

//val lgpio     = DefDevice(() => new APBGPIO     (AddrSpace(0x10002000, 0x10)))
//val lkeyboard = DefDevice(() => new APBKeyboard (AddrSpace(0x10011000, 0x8)))
//val lvga      = DefDevice(() => new APBVGA      (AddrSpace(0x21000000, 0x200000)))
  val lpsram    = DefDevice(() => new APBPSRAM    (AddrSpace(0x80000000L, 0x400000)))

  val bootDev = List(lspi, luart0, lpsram)
  val moreDev = List(lclint, lplic,
    lrcu, lrtc, lwdg, larchinfo,
    lgpio0, lgpio1, lgpio2, luart1, li2c, lps2, lpwm0, lpwm1, ltim0, ltim1, ltim2, ltim3,
    lqspi, li2s,
    lrng, lcrc
  )
  (bootDev ++ moreDev).map(_.map(_.node := apbxbar))
  if (false) {
    val xbar2 = AXI4Xbar()
    apbxbar := APBDelayer() := AXI4ToAPB() := AXI4Buffer() := xbar2
    val lmrom = LazyModule(new AXI4MROM(AddrSpace(0x20000000, 0x1000)))
    val sramNode = AXI4RAM(AddrSpace(0x02020000, 0x2000).head, false, true, 4, None, Nil, false)
    List(lmrom.node, sramNode).map(_ := xbar2)
    xbar2 := AXI4UserYanker(Some(1)) := AXI4Fragmenter() := xbar
  } else {
    apbxbar := APBDelayer() := AXI4ToAPB() := AXI4UserYanker(Some(1)) := AXI4Fragmenter() := xbar
  }

  xbar := cpu.masterNode

  override lazy val module = new Impl
  class Impl extends LazyModuleImp(this) with DontTouch {
    cpu.module.slave := DontCare

    val clock_half = IO(Input(Bool())) // external slower clock
    val intr = IO(Input(Bool()))
    cpu.module.interrupt := (if (isMini) false.B else lplic.get.module.irq_o)

    // for core multiplexing
    val coreSel = IO(Input(UInt(Config.coreSelWidth.W)))
    cpu.module.io_coreSel := coreSel

    lrcu.map { t =>
      val p = t.module.extra
      p.ext_lfosc_clk_i := false.B
      p.ext_hfosc_clk_i := false.B
      p.ext_audosc_clk_i := false.B
      p.ext_rst_n_i := false.B
      p.wdt_rst_n_i := false.B
      p.pll_en_i := false.B
      p.clk_cfg_i := false.B
      p.core_sel_i := false.B
    }

    List(ltim0, ltim1, ltim2, ltim3).map { t => t.map { x =>
      val p = x.module.extra
      p.capch_i := false.B
      p.exclk_i := clock_half
    }}

    List(lgpio0, lgpio1, lgpio2).map { t => t.map { x =>
      val p = x.module.extra
      p.gpio_in_i := 0.U
      p.gpio_alt_0_out_i := 0.U
      p.gpio_alt_0_dir_i := 0.U
      p.gpio_alt_1_out_i := 0.U
      p.gpio_alt_1_dir_i := 0.U
    }}

    lrtc.map { t =>
      val p = t.module.extra
      p.rtc_clk_i := clock_half
      p.rtc_rst_n_i := !reset.asBool
    }

    lwdg.map { t =>
      val p = t.module.extra
      p.rtc_clk_i := clock_half
    }

    li2c.map { t =>
      val p = t.module.extra
      val i2c_scl = IO(Analog(1.W))
      val i2c_sda = IO(Analog(1.W))
      p.scl_i := TriStateInBuf(i2c_scl, p.scl_o, p.scl_dir_o)
      p.sda_i := TriStateInBuf(i2c_sda, p.sda_o, p.sda_dir_o)
    }

    li2s.map { t =>
      val p = t.module.extra
      val i2s_sck = IO(Analog(1.W))
      val i2s_ws  = IO(Analog(1.W))
      p.sck_i := TriStateInBuf(i2s_sck, p.sck_o, p.sck_en_o)
      p.ws_i  := TriStateInBuf(i2s_ws, p.ws_o, p.ws_en_o)
      p.sd_i := false.B
    }

    // connect interrupt signal
    lplic.map { t =>
      t.module.extra.irq_i := Cat(List(lgpio0, lgpio1, lgpio2, lrtc, li2c, lqspi, li2s,
        lpwm0, lpwm1, ltim0, ltim1, ltim2, ltim3, lps2).map(_.get.module.irq_o)) ## intr
    }

    // expose slave I/O interface as ports
    def _genIO[T <: Data](name: String, inner: T) = {
      val outer = IO(chiselTypeOf(inner))
      outer.suggestName(name)
      outer <> inner
      outer
    }
    def genIO[T <: Data](name: String, inner: () => T, cond: Boolean = true) = {
      if (cond) Some(_genIO(name, inner())) else None
    }
    def genAPB4DevIO[T <: Data](name: String, lmodule: Option[APB4DevTemplate[T]], cond: Boolean = true) = {
      genIO(name, () => lmodule.get.module.extra, cond)
    }

    val uart0 = genAPB4DevIO("uart0", luart0)
    val spi   = genAPB4DevIO("spi", lspi)
    val psram = genAPB4DevIO("psram", lpsram)
    val uart1 = genAPB4DevIO("uart1", luart1, !isMini)
    val ps2   = genAPB4DevIO("ps2", lps2, !isMini)
    val gpio  = genIO("gpio", () => lgpio0.get.module.extra.gpio_out_o, !isMini)
    val qspi  = genAPB4DevIO("qspi", lqspi, !isMini)
    //val gpio  = genAPB4DevIO("gpio", lgpio)
    //val ps2   = genAPB4DevIO("ps2", lkeyboard)
    //val vga   = genAPB4DevIO("vga", lvga)
  }
}

class ysyxSoCASIC(implicit p: Parameters) extends LazyModule {
  val soc = LazyModule(new ysyxSoC)
  override lazy val module = new Impl
  class Impl extends LazyModuleImp(this) with DontTouch {
    val msoc = soc.module

    def genPAD[T <: Data](name: String, inner: T): T = {
      val outer = IO(chiselTypeOf(inner))
      outer.suggestName(name)
      GenPAD(outer, inner)
      dontTouch(outer)
      outer
    }
    def genPAD[T <: Data](name: String, inner: Option[T]): Option[T] = {
      if (inner != None) Some(genPAD(name, inner.get)) else None
    }

    GenPAD(clock, msoc.clock)
    GenPAD(reset, msoc.reset)
    val clock_half = genPAD("clock_half", msoc.clock_half)
    val coreSel = genPAD("coreSel", msoc.coreSel)
    val intr = genPAD("intr", msoc.intr)

    val uart0 = genPAD("uart0", msoc.uart0)
    val spi   = genPAD("spi", msoc.spi)
    val psram = genPAD("psram", msoc.psram)
    val uart1 = genPAD("uart1", msoc.uart1)
    val ps2   = genPAD("ps2", msoc.ps2)
    val gpio  = genPAD("gpio", msoc.gpio)
    val qspi  = genPAD("qspi", msoc.qspi)
  }
}

class ysyxSoCFull(implicit p: Parameters) extends LazyModule {
  val asic = LazyModule(new ysyxSoCASIC)
  ElaborationArtefacts.add("graphml", graphML)
  val isMini = Config.isMini

  override lazy val module = new Impl
  class Impl extends LazyModuleImp(this) with DontTouch {
    val masic = asic.module

    // slower clock
    val divReg = RegInit(false.B)
    divReg := !divReg
    masic.clock_half := divReg
    masic.intr := false.B

    // for core multiplexing
    val coreSel = IO(Input(UInt(Config.coreSelWidth.W)))
    masic.coreSel := coreSel

    if (!isMini) {
      val gpio_led = Module(new gpio_led_model)
      gpio_led.io.led_i := masic.gpio.get

      masic.ps2.get.ps2_clk_i := false.B
      masic.ps2.get.ps2_dat_i := false.B
      masic.qspi.get.spi_io_in_i := false.B
      masic.uart1.get.uart_rx_i := false.B
    }

    val flash = Module(new flash)
    flash.io <> masic.spi.get
    flash.io.ss := masic.spi.get.ss(0)
    val bitrev = Module(new bitrev)
    bitrev.io <> masic.spi.get
    bitrev.io.ss := masic.spi.get.ss(7)
    masic.spi.get.miso := List(bitrev.io, flash.io).map(_.miso).reduce(_&&_)

    val psram = Module(new ESPWrapper)
    psram.io <> masic.psram.get

    val externalPins = IO(new Bundle{
      //val gpio = chiselTypeOf(masic.gpio)
      //val ps2 = chiselTypeOf(masic.ps2)
      //val vga = chiselTypeOf(masic.vga)
      val uart0 = chiselTypeOf(masic.uart0.get)
      //val uart1 = chiselTypeOf(masic.uart1)
    })
    //externalPins.gpio <> masic.gpio
    //externalPins.ps2 <> masic.ps2
    //externalPins.vga <> masic.vga
    externalPins.uart0 <> masic.uart0.get
    //externalPins.uart1 <> masic.uart1
  }
}
