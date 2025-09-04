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

class ysyxSoCASIC(implicit p: Parameters) extends LazyModule {
  val xbar = AXI4Xbar()
  val apbxbar = LazyModule(new APBFanout).node
  val cpu = LazyModule(new CPU(idBits = Config.idBits))

  def AddrSpace(base: BigInt, len: BigInt = 0x1000) = AddressSet.misaligned(base, len)

  // RISC-V system
  val lclint    = LazyModule(new APB4CLINT   (AddrSpace(0x02010000, 0x10000)))
  val lplic     = LazyModule(new APB4PLIC    (AddrSpace(0x0c000000, 0x40)))

  // generic system
  val luart0    = LazyModule(new APBUart16550(AddrSpace(0x10000000, 0x8)))
  val lspi      = LazyModule(new APBSPI      (AddrSpace(0x10001000, 0x20)   ++     // SPI controller
                                              AddrSpace(0x30000000, 0x10000000)))  // XIP flash
  val lrtc      = LazyModule(new APB4RTC     (AddrSpace(0x10004000, 0x20)))
  val lwdg      = LazyModule(new APB4WDG     (AddrSpace(0x10005000, 0x20)))
  val larchinfo = LazyModule(new APB4ArchInfo(AddrSpace(0x10006000, 0x10)))

  // interface
  val lgpio0    = LazyModule(new APB4GPIO    (AddrSpace(0x10100000, 0x40)))
  val lgpio1    = LazyModule(new APB4GPIO    (AddrSpace(0x10101000, 0x40)))
  val lgpio2    = LazyModule(new APB4GPIO    (AddrSpace(0x10102000, 0x40)))
  val luart1    = LazyModule(new APB4UART    (AddrSpace(0x10103000, 0x20)))
  val li2c      = LazyModule(new APB4I2C     (AddrSpace(0x10104000, 0x20)))
  val lps2      = LazyModule(new APB4PS2     (AddrSpace(0x10105000, 0x10)))
  val lpwm0     = LazyModule(new APB4PWM     (AddrSpace(0x10106000, 0x40)))
  val lpwm1     = LazyModule(new APB4PWM     (AddrSpace(0x10107000, 0x40)))
  val ltim0     = LazyModule(new APB4Timer   (AddrSpace(0x10108000, 0x20)))
  val ltim1     = LazyModule(new APB4Timer   (AddrSpace(0x10109000, 0x20)))
  val ltim2     = LazyModule(new APB4Timer   (AddrSpace(0x1010a000, 0x20)))
  val ltim3     = LazyModule(new APB4Timer   (AddrSpace(0x1010b000, 0x20)))

  // multimedia
  val lqspi     = LazyModule(new APB4QSPI    (AddrSpace(0x10200000, 0x20)))
  val li2s      = LazyModule(new APB4I2S     (AddrSpace(0x10201000, 0x20)))

  // application
  val lrng      = LazyModule(new APB4RNG     (AddrSpace(0x10300000, 0x10)))
  val lcrc      = LazyModule(new APB4CRC     (AddrSpace(0x10301000, 0x20)))

//val lgpio     = LazyModule(new APBGPIO     (AddrSpace(0x10002000, 0x10)))
//val lkeyboard = LazyModule(new APBKeyboard (AddrSpace(0x10011000, 0x8)))
//val lvga      = LazyModule(new APBVGA      (AddrSpace(0x21000000, 0x200000)))
  val lpsram    = LazyModule(new APBPSRAM    (AddrSpace(0x80000000L, 0x400000)))

  List(lclint, lplic,
       lspi, luart0,
       lrtc, lwdg, larchinfo,
       lgpio0, lgpio1, lgpio2, luart1, li2c, lps2, lpwm0, lpwm1, ltim0, ltim1, ltim2, ltim3,
       lqspi, li2s,
       lrng, lcrc,
       lpsram
  ).map(_.node := apbxbar)
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

    // external slower clock
    val clock_half = IO(Input(Bool()))

    List(ltim0, ltim1, ltim2, ltim3).map { t =>
      t.module.extra.capch_i := false.B
      t.module.extra.exclk_i := clock_half
    }
    List(lgpio0, lgpio1, lgpio2).map { t =>
      t.module.extra.gpio_in_i := 0.U
      t.module.extra.gpio_alt_0_out_i := 0.U
      t.module.extra.gpio_alt_0_dir_i := 0.U
      t.module.extra.gpio_alt_1_out_i := 0.U
      t.module.extra.gpio_alt_1_dir_i := 0.U
    }
    lrtc.module.extra.rtc_clk_i := clock_half
    lrtc.module.extra.rtc_rst_n_i := !reset.asBool
    lwdg.module.extra.rtc_clk_i := clock_half

    val i2c_io = li2c.module.extra
    val i2c_scl = IO(Analog(1.W))
    val i2c_sda = IO(Analog(1.W))
    i2c_io.scl_i := TriStateInBuf(i2c_scl, i2c_io.scl_o, i2c_io.scl_dir_o)
    i2c_io.sda_i := TriStateInBuf(i2c_sda, i2c_io.sda_o, i2c_io.sda_dir_o)

    val i2s_io = li2s.module.extra
    val i2s_sck = IO(Analog(1.W))
    val i2s_ws  = IO(Analog(1.W))
    i2s_io.sck_i := TriStateInBuf(i2s_sck, i2s_io.sck_o, i2s_io.sck_en_o)
    i2s_io.ws_i  := TriStateInBuf(i2s_ws, i2s_io.ws_o, i2s_io.ws_en_o)
    i2s_io.sd_i := false.B

    val qspi_io = lqspi.module.extra
    val qspi_sck_o = IO(Output(Bool()))
    val qspi_nss_o = IO(Output(UInt(4.W)))
    val qspi_dio  = IO(Vec(4, Analog(1.W)))
    qspi_sck_o := qspi_io.spi_sck_o
    qspi_nss_o := qspi_io.spi_nss_o
    qspi_io.spi_io_in_i := Cat((0 to 3).map(i => TriStateInBuf(qspi_dio(i), qspi_io.spi_io_out_o(i), qspi_io.spi_io_en_o(i))).reverse)

    // connect interrupt signal
    val intr = IO(Input(Bool()))
    cpu.module.interrupt := lplic.module.irq_o
    lplic.module.extra.irq_i := Cat(List(lgpio0, lgpio1, lgpio2, lrtc, li2c, lqspi, li2s,
      lpwm0, lpwm1, ltim0, ltim1, ltim2, ltim3, lps2).map(_.module.irq_o)) ## intr

    // expose slave I/O interface as ports
    def genIO[T <: Data](name: String, inner: T) = {
      val outer = IO(chiselTypeOf(inner))
      outer.suggestName(name)
      outer <> inner
      outer
    }
    def genAPB4DevIO[T <: Data](name: String, lmodule: APB4DevTemplate[T]) = genIO(name, lmodule.module.extra)
    def genSomeAPB4DevIO[T <: Data](name: String, lmodule: Option[APB4DevTemplate[T]]) = {
      if (false) Some(genAPB4DevIO(name, lmodule.get)) else None
    }

    val uart0 = genAPB4DevIO("uart0", luart0)
    val uart1 = genAPB4DevIO("uart1", luart1)
    val spi   = genAPB4DevIO("spi", lspi)
    val psram = genAPB4DevIO("psram", lpsram)
    val ps2   = genAPB4DevIO("ps2", lps2)
    val gpio  = genIO("gpio", lgpio0.module.extra.gpio_out_o)
    //val gpio  = genAPB4DevIO("gpio", lgpio)
    //val ps2   = genAPB4DevIO("ps2", lkeyboard)
    //val vga   = genAPB4DevIO("vga", lvga)
  }
}

class ysyxSoCFull(implicit p: Parameters) extends LazyModule {
  val asic = LazyModule(new ysyxSoCASIC)
  ElaborationArtefacts.add("graphml", graphML)

  override lazy val module = new Impl
  class Impl extends LazyModuleImp(this) with DontTouch {
    val masic = asic.module

    // slower clock
    val divReg = RegInit(false.B)
    divReg := !divReg
    masic.clock_half := divReg

    masic.intr := false.B

    val gpio_led = Module(new gpio_led_model)
    gpio_led.io.led_i := masic.gpio

    masic.ps2.ps2_clk_i := false.B
    masic.ps2.ps2_dat_i := false.B

    val flash = Module(new flash)
    flash.io <> masic.spi
    flash.io.ss := masic.spi.ss(0)
    val bitrev = Module(new bitrev)
    bitrev.io <> masic.spi
    bitrev.io.ss := masic.spi.ss(7)
    masic.spi.miso := List(bitrev.io, flash.io).map(_.miso).reduce(_&&_)

    val psram = Module(new ESPWrapper)
    psram.io <> masic.psram

    val externalPins = IO(new Bundle{
      //val gpio = chiselTypeOf(masic.gpio)
      //val ps2 = chiselTypeOf(masic.ps2)
      //val vga = chiselTypeOf(masic.vga)
      val uart0 = chiselTypeOf(masic.uart0)
      val uart1 = chiselTypeOf(masic.uart1)
    })
    //externalPins.gpio <> masic.gpio
    //externalPins.ps2 <> masic.ps2
    //externalPins.vga <> masic.vga
    externalPins.uart0 <> masic.uart0
    externalPins.uart1 <> masic.uart1
  }
}
