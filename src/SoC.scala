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

import scala.collection.mutable.Map

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

object AsyncResetSynchronizer {
  def apply(clock: Clock, reset: Reset): Reset = {  // Synchronize `reset` to the `clock` domain
    withClockAndReset (clock, reset.asAsyncReset) {
      ~RegNext(RegNext(true.B, init = false.B), init = false.B)
    }
  }
}

object CHeader {
  val map: Map[String, AddressSet] = Map()
  def init() = { map.clear() }
  def add(name: String, range: AddressSet, idx: Int = 0): Unit = {
    val realName = name + (if (idx == 0) "" else f"_${idx}")
    if (map.get(realName) == None) { map += (realName -> range) }
    else { add(name, range, idx + 1) }
  }
  def write() = {
    val codeBase = map.toSeq.sortBy(_._2).map(m => {
      val name = m._1 + "_BASE"
      f"#define ${name}%-20s 0x${m._2.base}%08x\n"
    }).reduce(_ + _)
    val codeLen = map.toSeq.sortBy(_._2).map(m => {  // assume that AddresssSet is aligned
      val name = m._1 + "_LEN"
      f"#define ${name}%-20s 0x${m._2.mask + 1}%x\n"
    }).reduce(_ + _)

    val code = "#ifndef __MMIO_H__\n#define __MMIO_H__\n\n" + codeBase + "\n" + codeLen + "\n#endif"
    java.nio.file.Files.write(java.nio.file.Paths.get("build/mmio.h"), code.getBytes)
  }
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

  CHeader.init()

  // RISC-V system
  val lclint    = DefDevice(() => new APB4CLINT   (AddrSpace(0x02010000, 0x10000)), !isMini)
  val lplic     = DefDevice(() => new APB4PLIC    (AddrSpace(0x0c000000, 0x40)), Config.hasIntr)

  // generic system
  val luart0    = DefDevice(() => new APBUart16550(AddrSpace(0x10000000, 0x8)))
  val lspi      = DefDevice(() => new APBSPI      (AddrSpace(0x10001000, 0x20)   ++     // SPI controller
                                                   AddrSpace(0x30000000, 0x10000000),   // XIP flash
                                                   if (Config.hasMoreHomework) 8 else 1))
  val lrcu      = DefDevice(() => new APB4RCU     (AddrSpace(0x10002000, 0x1000)), Config.hasPLL)
  val lrtc      = DefDevice(() => new APB4RTC     (AddrSpace(0x10004000, 0x20)), !isMini)
  val lwdg      = DefDevice(() => new APB4WDG     (AddrSpace(0x10005000, 0x20)), !isMini)
  val larchinfo = DefDevice(() => new APB4ArchInfo(AddrSpace(0x10006000, 0x10)), !isMini)

  // interface
  val lgpio0    = DefDevice(() => new APB4GPIO    (AddrSpace(0x10100000, 0x40), width = 4), !isMini)
  val lgpio1    = DefDevice(() => new APB4GPIO    (AddrSpace(0x10101000, 0x40)), false)
  val lgpio2    = DefDevice(() => new APB4GPIO    (AddrSpace(0x10102000, 0x40)), false)
  val luart1    = DefDevice(() => new APB4UART    (AddrSpace(0x10103000, 0x20)), !isMini)
  val li2c      = DefDevice(() => new APB4I2C     (AddrSpace(0x10104000, 0x20)), !isMini)
  val lps2      = DefDevice(() => new APB4PS2     (AddrSpace(0x10105000, 0x10)), false)
  val lpwm0     = DefDevice(() => new APB4PWM     (AddrSpace(0x10106000, 0x40)), !isMini)
  val lpwm1     = DefDevice(() => new APB4PWM     (AddrSpace(0x10107000, 0x40)), false)
  val ltim0     = DefDevice(() => new APB4Timer   (AddrSpace(0x10108000, 0x20)), !isMini)
  val ltim1     = DefDevice(() => new APB4Timer   (AddrSpace(0x10109000, 0x20)), !isMini)
  val ltim2     = DefDevice(() => new APB4Timer   (AddrSpace(0x1010a000, 0x20)), !isMini)
  val ltim3     = DefDevice(() => new APB4Timer   (AddrSpace(0x1010b000, 0x20)), !isMini)

  // multimedia
  val lqspi     = DefDevice(() => new APB4QSPI    (AddrSpace(0x10200000, 0x20), nss = 2), !isMini)
  val li2s      = DefDevice(() => new APB4I2S     (AddrSpace(0x10201000, 0x20)), false)

  // application
  val lrng      = DefDevice(() => new APB4RNG     (AddrSpace(0x10300000, 0x10)), !isMini)
  val lcrc      = DefDevice(() => new APB4CRC     (AddrSpace(0x10301000, 0x20)), !isMini)

  // homework
  val lmygpio   = DefDevice(() => new APB4MyGPIO  (AddrSpace(0x20001000, 0x10)), Config.hasHomework)
  val lmykbd    = DefDevice(() => new APB4MyKbd   (AddrSpace(0x20002000, 0x8)), Config.hasMoreHomework)
  val lmyvga    = DefDevice(() => new APB4MyVGA   (AddrSpace(0x21000000, 0x200000)), Config.hasMoreHomework)

  // memory
  val lpsram    = DefDevice(() => new APBPSRAM    (AddrSpace(0x80000000L, 0x400000), nss = 3))

  CHeader.write()

  val bootDev = List(lspi, luart0, lpsram)
  val moreDev = List(lclint, lplic,
    lrcu, lrtc, lwdg, larchinfo,
    lgpio0, lgpio1, lgpio2, luart1, li2c, lps2, lpwm0, lpwm1, ltim0, ltim1, ltim2, ltim3,
    lqspi, li2s,
    lrng, lcrc
  )
  val homeworkDev = List(lmygpio, lmykbd, lmyvga)
  (bootDev ++ moreDev ++ homeworkDev).map(_.map(_.node := apbxbar))

  val tmpNode = if (Config.isSimpleBus) xbar else {
    val tmpNode2 = if (Config.hasMoreHomework) {
      val xbar2 = AXI4Xbar()
      val lmrom = LazyModule(new AXI4MROM(AddrSpace(0x20000000, 0x1000)))
      val sramNode = AXI4RAM(AddrSpace(0x02020000, 0x2000).head, false, true, 4, None, Nil, false)
      List(lmrom.node, sramNode).map(_ := xbar2)
      xbar2
    } else AXI4Buffer()
    tmpNode2 := AXI4UserYanker(Some(1)) := AXI4Fragmenter() := xbar
  }
  val apbDelayer = if (Config.hasHomework) APBDelayer() else APBIdentityNode()
  apbxbar := apbDelayer := AXI4ToAPB() := tmpNode
  xbar := cpu.masterNode

  override lazy val module = new Impl
  class Impl extends LazyModuleImp(this) with DontTouch {
    cpu.module.slave := DontCare

    cpu.module.interrupt := lplic.map(_.module.irq_o).getOrElse(false.B)

    // for core multiplexing
    val coreSel = if (Config.numCore > 1) Some(IO(Input(UInt(Config.coreSelWidth.W)))) else None
    cpu.module.io_coreSel := coreSel.getOrElse(0.U)

    lrcu.map { t =>
      val p = t.module.extra
      p.ext_lfosc_clk_i := clock.asBool
      p.ext_hfosc_clk_i := false.B   // unused
      p.ext_audosc_clk_i := false.B  // unused
      p.ext_rst_n_i := !reset.asBool
      p.wdt_rst_n_i := lwdg.map(_.module.extra.rst_o).getOrElse(false.B)
      p.core_sel_i := false.B        // unused
    }

    List(ltim0, ltim1, ltim2, ltim3).map(_.map(_.module.extra.exclk_i := clock.asBool))

    List(lgpio0, lgpio1, lgpio2).map(_.map { x =>
      val p = x.module.extra
      p.gpio_alt_0_out_i := 0.U
      p.gpio_alt_0_dir_i := 0.U
      p.gpio_alt_1_out_i := 0.U
      p.gpio_alt_1_dir_i := 0.U
    })

    lrtc.map { t =>
      val p = t.module.extra
      p.rtc_clk_i := clock.asBool
      p.rtc_rst_n_i := !reset.asBool
    }

    lwdg.map(_.module.extra.rtc_clk_i := clock.asBool)

    // connect interrupt signal
    lplic.map(_.module.extra.irq_i := Cat(List(lgpio0, lgpio1, lgpio2, lrtc, li2c, lqspi, li2s,
      lpwm0, lpwm1, ltim0, ltim1, ltim2, ltim3, lps2).filter(_ != None).map(_.get.module.irq_o)))

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
    def genAPB4DevIO[T <: Data](name: String, lmodule: Option[APB4DevTemplate[T]]) = {
      genIO(name, () => lmodule.get.module.extra, lmodule != None)
    }

    val uart0 = genAPB4DevIO("uart0", luart0)
    val spi   = genAPB4DevIO("spi", lspi)
    val psram = genAPB4DevIO("psram", lpsram)

    val pll_en_i  = genIO("pll_en_i",  () => lrcu.get.module.extra.pll_en_i, lrcu != None)
    val clk_cfg_i = genIO("clk_cfg_i", () => lrcu.get.module.extra.clk_cfg_i, lrcu != None)
    val clk_soc_o = genIO("clk_soc_o", () => lrcu.get.module.extra.clk_o(0), lrcu != None)

    val gpio0 = genAPB4DevIO("gpio0", lgpio0)
    //val gpio1 = genAPB4DevIO("gpio1", lgpio1)
    //val gpio2 = genAPB4DevIO("gpio2", lgpio2)
    val uart1 = genAPB4DevIO("uart1", luart1)
    val i2c   = genAPB4DevIO("i2c", li2c)
    val ps2   = genAPB4DevIO("ps2", lps2)
    val pwm0  = genAPB4DevIO("pwm0", lpwm0)
    val pwm1  = genAPB4DevIO("pwm1", lpwm1)
    val tim0_capch = genIO("tim0_capch", () => ltim0.get.module.extra.capch_i, false)
    val tim1_capch = genIO("tim1_capch", () => ltim1.get.module.extra.capch_i, false)
    val tim2_capch = genIO("tim2_capch", () => ltim2.get.module.extra.capch_i, false)
    val tim3_capch = genIO("tim3_capch", () => ltim3.get.module.extra.capch_i, false)
    List(ltim0, ltim1, ltim2, ltim3).map(_.map(_.module.extra.capch_i := false.B))
    val qspi  = genAPB4DevIO("qspi", lqspi)
    val i2s   = genAPB4DevIO("i2s", li2s)

    val mygpio = genAPB4DevIO("mygpio", lmygpio)
    val mykbd  = genAPB4DevIO("mykbd", lmykbd)
    val myvga  = genAPB4DevIO("myvga", lmyvga)
  }
}

class asicTop(implicit p: Parameters) extends LazyModule {
  val soc = LazyModule(new ysyxSoC)
  override lazy val module = new Impl
  class Impl extends LazyModuleImp(this) with DontTouch {
    val msoc = soc.module

    def genPAD[T <: Data](name: String, inner: T)(implicit dir: PadDirection): T = {
      val outer = IO(chiselTypeOf(inner))
      outer.suggestName(name)
      GenPAD(outer, inner)
      dontTouch(outer)
      outer
    }
    def genPAD[T <: Data](name: String, inner: Option[T])(implicit dir: PadDirection): Option[T] = {
      if (inner != None) Some(genPAD(name, inner.get)) else None
    }
    def genPAD(name: String, p2c: UInt, c2p: UInt, c2pEn: UInt)(implicit dir: PadDirection): Vec[Analog] = {
      val outer = IO(Vec(p2c.getWidth, Analog(1.W)))
      outer.suggestName(name)
      GenPAD(outer, p2c, c2p, c2pEn)
      dontTouch(outer)
      outer
    }
    def genIOPAD(name: String, p2c_c2p_c2pEn: Option[(UInt, UInt, UInt)])(implicit dir: PadDirection): Option[Vec[Analog]] = {
      p2c_c2p_c2pEn.flatMap(x => Some(genPAD(name, x._1, x._2, x._3)))
    }

    val clock_pad_i = IO(Input(Clock()))
    val clock_pad_o = IO(Output(Clock()))
    GenPAD(clock_pad_i, msoc.clock, clock_pad_o)(VPad())

    val resetn_pad_i = IO(Input(Bool()))
    val resetn = Wire(Bool())
    GenPAD(resetn_pad_i, resetn)(VPad())
    msoc.reset := AsyncResetSynchronizer(msoc.clock, ~resetn)

    val coreSel = genPAD("coreSel", msoc.coreSel)(HPad())

    val pll_en_i  = genPAD("pll_en_i", msoc.pll_en_i)(HPad())
    val clk_cfg_i = genPAD("clk_cfg_i", msoc.clk_cfg_i)(HPad())
    val clk_soc_o = genPAD("clk_soc_o", msoc.clk_soc_o)(VPad())

    val uart0 = genPAD("uart0", msoc.uart0)(VPad())
    val spi   = genPAD("spi", msoc.spi)(VPad())

    val psram = msoc.psram.get
    val psram_sck_o = genPAD("psram_sck_o", psram.sck_o)(VPad())
    val psram_nss_o = genPAD("psram_nss_o", psram.nss_o)(HPad())
    val psram_dio   = genPAD("psram_dio", psram.io_di_i, psram.io_do_o, psram.io_oe_o)(HPad())

    val gpio0      = genIOPAD("gpio0", msoc.gpio0.flatMap(x => Some(x.gpio_in_i, x.gpio_out_o, x.gpio_dir_o)))(VPad())
    msoc.gpio0.map { x =>
      x.gpio_alt_0_out_i := 0.U
      x.gpio_alt_0_dir_i := 0.U
      x.gpio_alt_1_out_i := 0.U
      x.gpio_alt_1_dir_i := 0.U
    }

    val uart1      = genPAD("uart1", msoc.uart1)(HPad())
    val i2c_scl    = genIOPAD("i2c_scl", msoc.i2c.flatMap(x => Some(x.scl_i, x.scl_o, x.scl_dir_o)))(VPad())
    val i2c_sda    = genIOPAD("i2c_sda", msoc.i2c.flatMap(x => Some(x.sda_i, x.sda_o, x.sda_dir_o)))(VPad())
    val ps2        = genPAD("ps2", msoc.ps2)(NPad())
    val pwm0       = genPAD("pwm0", msoc.pwm0)(NPad())
    val pwm1       = genPAD("pwm1", msoc.pwm1)(NPad())
    val tim0_capch = genPAD("tim0_capch", msoc.tim0_capch)(NPad())
    val tim1_capch = genPAD("tim1_capch", msoc.tim1_capch)(NPad())
    val tim2_capch = genPAD("tim2_capch", msoc.tim2_capch)(NPad())
    val tim3_capch = genPAD("tim3_capch", msoc.tim3_capch)(NPad())
    val qspi_sck_o = genPAD("qspi_sck_o", msoc.qspi.flatMap(x => Some(x.spi_sck_o)))(VPad())
    val qspi_nss_o = genPAD("qspi_nss_o", msoc.qspi.flatMap(x => Some(x.spi_nss_o)))(VPad())
    val qspi_dio   = genIOPAD("qspi_dio", msoc.qspi.flatMap(x => Some(x.spi_io_in_i, x.spi_io_out_o, x.spi_io_en_o)))(VPad())
    val i2s_sck    = genIOPAD("i2s_sck", msoc.i2s.flatMap(x => Some(x.sck_i, x.sck_o, x.sck_en_o)))(NPad())
    val i2s_ws     = genIOPAD("i2s_ws",  msoc.i2s.flatMap(x => Some(x.ws_i,  x.ws_o,  x.ws_en_o )))(NPad())
    val i2s_sd_i   = genPAD("i2s_sd_i", msoc.i2s.flatMap(x => Some(x.sd_i)))(NPad())

    val mygpio = genPAD("mygpio", msoc.mygpio)(NPad())
    val mykbd  = genPAD("mykbd", msoc.mykbd)(NPad())
    val myvga  = genPAD("myvga", msoc.myvga)(NPad())
  }
}

class SimTop(implicit p: Parameters) extends LazyModule {
  val asic = LazyModule(new asicTop)
  ElaborationArtefacts.add("graphml", graphML)

  override lazy val module = new Impl
  class Impl extends LazyModuleImp(this) with DontTouch {
    val masic = asic.module

    masic.clock_pad_i := clock
    dontTouch(masic.clock_pad_o)
    masic.resetn_pad_i := ~reset.asBool

    // for core multiplexing
    val coreSel = IO(Input(UInt(Config.coreSelWidth.W)))
    masic.coreSel.map(_ <> coreSel)

    masic.pll_en_i.map(_ <> DontCare)
    masic.clk_cfg_i.map(_ <> DontCare)

    //val gpio_led = Module(new gpio_led_model)
    //gpio_led.io.led_i := masic.gpio0.get
    masic.gpio0.map(_ <> DontCare)
    masic.i2c_scl.map(_ <> DontCare)
    masic.i2c_sda.map(_ <> DontCare)
    masic.ps2.map(_ <> DontCare)
    masic.tim0_capch.map(_ <> DontCare)
    masic.tim1_capch.map(_ <> DontCare)
    masic.tim2_capch.map(_ <> DontCare)
    masic.tim3_capch.map(_ <> DontCare)
    masic.qspi_dio.map(_ <> DontCare)
    masic.i2s_sck.map(_ <> DontCare)
    masic.i2s_ws.map(_ <> DontCare)
    masic.i2s_sd_i.map(_ <> DontCare)

    val flash = Module(new flash)
    flash.io <> masic.spi.get
    flash.io.ss := masic.spi.get.ss(0)
    if (Config.hasMoreHomework) {
      val bitrev = Module(new bitrev)
      bitrev.io <> masic.spi.get
      bitrev.io.ss := masic.spi.get.ss(7)
      masic.spi.get.miso := List(bitrev.io, flash.io).map(_.miso).reduce(_&&_)
    } else {
      masic.spi.get.miso := flash.io.miso
    }

    val espPsram = Module(new ESPWrapper)
    espPsram.io.sck_o := masic.psram_sck_o
    espPsram.io.nss_o  := masic.psram_nss_o
    espPsram.io.dio <> masic.psram_dio

    val externalPins = IO(new Bundle{
      val mygpio = masic.mygpio.flatMap(x => Some(chiselTypeOf(x)))
      val mykbd = masic.mykbd.flatMap(x => Some(chiselTypeOf(x)))
      val myvga = masic.myvga.flatMap(x => Some(chiselTypeOf(x)))
      val uart0 = masic.uart0.flatMap(x => Some(chiselTypeOf(x)))
      val uart1 = masic.uart1.flatMap(x => Some(chiselTypeOf(x)))
    })
    externalPins.mygpio.map(_ <> masic.mygpio.get)
    externalPins.mykbd.map(_ <> masic.mykbd.get)
    externalPins.myvga.map(_ <> masic.myvga.get)
    externalPins.uart0.map(_ <> masic.uart0.get)
    externalPins.uart1.map(_ <> masic.uart1.get)
  }
}
