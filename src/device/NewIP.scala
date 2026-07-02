package ysyx

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy._

class apb4_archinfo extends BlackBoxWithAPB4
class APB4ArchInfo(address: Seq[AddressSet])(implicit p: Parameters) extends APB4DevBlackBox(address, () => new apb4_archinfo)

class CLINTBundle extends Bundle {
  val tmr_irq_o = Output(Bool())
  val sfr_irq_o = Output(Bool())
}
class CLINTIO extends MyAPB4Bundle {
  val clint = new CLINTBundle
  override def connect_extra(extra: Data): Unit = extra.asInstanceOf[CLINTBundle] <> this.clint
}
class apb4_clint extends BlackBoxWithAPB4(new CLINTIO)
class APB4CLINT(address: Seq[AddressSet])(implicit p: Parameters) extends APB4DevBlackBox(address, () => new apb4_clint, new CLINTBundle)

class apb4_crc extends BlackBoxWithAPB4
class APB4CRC(address: Seq[AddressSet])(implicit p: Parameters) extends APB4DevBlackBox(address, () => new apb4_crc)

class GPIOBundle extends Bundle {
  val gpio_in_i = Input(UInt(32.W))
  val gpio_out_o = Output(UInt(32.W))
  val gpio_dir_o = Output(UInt(32.W))
  val gpio_alt_in_o = Output(UInt(32.W))
  val gpio_alt_0_out_i = Input(UInt(32.W))
  val gpio_alt_0_dir_i = Input(UInt(32.W))
  val gpio_alt_1_out_i = Input(UInt(32.W))
  val gpio_alt_1_dir_i = Input(UInt(32.W))
}
class GPIOnewIO extends MyAPB4Bundle {
  val gpio = new GPIOBundle
  val gpio_irq_o = Output(Bool())
  override def connect_extra(extra: Data): Unit = extra.asInstanceOf[GPIOBundle] <> this.gpio
  override def connect_irq(irq_o: Bool): Unit = irq_o := gpio_irq_o
}
class apb4_gpio extends BlackBoxWithAPB4(new GPIOnewIO)
class APB4GPIO(address: Seq[AddressSet])(implicit p: Parameters) extends APB4DevBlackBox(address, () => new apb4_gpio, new GPIOBundle, true)

class gpio_led_model extends BlackBox {
  val io = IO(new Bundle {
    val led_i = Input(UInt(32.W))
  })
}

class I2CBundle extends Bundle {
  val scl_i = Input(Bool())
  val scl_o = Output(Bool())
  val scl_dir_o = Output(Bool())
  val sda_i = Input(Bool())
  val sda_o = Output(Bool())
  val sda_dir_o = Output(Bool())
}
class I2CIO extends MyAPB4Bundle {
  val i2c = new I2CBundle
  val i2c_irq_o = Output(Bool())
  override def connect_extra(extra: Data): Unit = extra.asInstanceOf[I2CBundle] <> this.i2c
  override def connect_irq(irq_o: Bool): Unit = irq_o := i2c_irq_o
}
class apb4_i2c extends BlackBoxWithAPB4(new I2CIO)
class APB4I2C(address: Seq[AddressSet])(implicit p: Parameters) extends APB4DevBlackBox(address, () => new apb4_i2c, new I2CBundle, true)

class I2SBundle extends Bundle {
  val mclk_o = Output(Bool())
  val sck_o = Output(Bool())
  val sck_i = Input(Bool())
  val sck_en_o = Output(Bool())
  val ws_o = Output(Bool())
  val ws_i = Input(Bool())
  val ws_en_o = Output(Bool())
  val sd_o = Output(Bool())
  val sd_i = Input(Bool())
}
class I2SIO extends MyAPB4Bundle {
  val i2s = new I2SBundle
  val i2s_irq_o = Output(Bool())
  override def connect_extra(extra: Data): Unit = extra.asInstanceOf[I2SBundle] <> this.i2s
  override def connect_irq(irq_o: Bool): Unit = irq_o := i2s_irq_o
}
class apb4_i2s extends BlackBoxWithAPB4(new I2SIO)
class APB4I2S(address: Seq[AddressSet])(implicit p: Parameters) extends APB4DevBlackBox(address, () => new apb4_i2s, new I2SBundle, true)

class PLICBundle extends Bundle {
  val irq_i = Input(UInt(32.W))
}
class PLICIO extends MyAPB4Bundle {
  val plic = new PLICBundle
  val plic_irq_o = Output(Bool())
  override def connect_extra(extra: Data): Unit = extra.asInstanceOf[PLICBundle] <> this.plic
  override def connect_irq(irq_o: Bool): Unit = irq_o := plic_irq_o
}
class apb4_plic extends BlackBoxWithAPB4(new PLICIO)
class APB4PLIC(address: Seq[AddressSet])(implicit p: Parameters) extends APB4DevBlackBox(address, () => new apb4_plic, new PLICBundle, true)

class PS2Bundle extends Bundle {
  val ps2_clk_i = Input(Bool())
  val ps2_dat_i = Input(Bool())
}
class PS2newIO extends MyAPB4Bundle {
  val ps2 = new PS2Bundle
  val ps2_irq_o = Output(Bool())
  override def connect_extra(extra: Data): Unit = extra.asInstanceOf[PS2Bundle] <> this.ps2
  override def connect_irq(irq_o: Bool): Unit = irq_o := ps2_irq_o
}
class apb4_ps2 extends BlackBoxWithAPB4(new PS2newIO)
class APB4PS2(address: Seq[AddressSet])(implicit p: Parameters) extends APB4DevBlackBox(address, () => new apb4_ps2, new PS2Bundle, true)

class PWMBundle extends Bundle {
  val pwm_o = Output(UInt(4.W))
}
class PWMIO extends MyAPB4Bundle {
  val pwm = new PWMBundle
  val pwm_irq_o = Output(Bool())
  override def connect_extra(extra: Data): Unit = extra.asInstanceOf[PWMBundle] <> this.pwm
  override def connect_irq(irq_o: Bool): Unit = irq_o := pwm_irq_o
}
class apb4_pwm extends BlackBoxWithAPB4(new PWMIO)
class APB4PWM(address: Seq[AddressSet])(implicit p: Parameters) extends APB4DevBlackBox(address, () => new apb4_pwm, new PWMBundle, true)

class apb4_rng extends BlackBoxWithAPB4
class APB4RNG(address: Seq[AddressSet])(implicit p: Parameters) extends APB4DevBlackBox(address, () => new apb4_rng)

class RTCBundle extends Bundle {
  val rtc_clk_i = Input(Bool())
  val rtc_rst_n_i = Input(Bool())
}
class RTCIO extends MyAPB4Bundle {
  val rtc = new RTCBundle
  val rtc_irq_o = Output(Bool())
  override def connect_extra(extra: Data): Unit = extra.asInstanceOf[RTCBundle] <> this.rtc
  override def connect_irq(irq_o: Bool): Unit = irq_o := rtc_irq_o
}
class apb4_rtc extends BlackBoxWithAPB4(new RTCIO)
class APB4RTC(address: Seq[AddressSet])(implicit p: Parameters) extends APB4DevBlackBox(address, () => new apb4_rtc, new RTCBundle, true)

class QSPIBundle(nss: Int = 4) extends Bundle {
  val spi_sck_o = Output(Bool())
  val spi_nss_o = Output(UInt(nss.W))
  val spi_io_en_o = Output(UInt(4.W))
  val spi_io_in_i = Input(UInt(4.W))
  val spi_io_out_o = Output(UInt(4.W))
}
class QSPIIO(nss: Int = 4) extends MyAPB4Bundle {
  val qspi = new QSPIBundle(nss)
  val qspi_irq_o = Output(Bool())
  override def connect_extra(extra: Data): Unit = extra.asInstanceOf[QSPIBundle] <> this.qspi
  override def connect_irq(irq_o: Bool): Unit = irq_o := qspi_irq_o
}
class apb4_spi extends BlackBoxWithAPB4(new QSPIIO(4))
class APB4QSPI(address: Seq[AddressSet], nss: Int = 4)(implicit p: Parameters) extends APB4DevBlackBox(address, () => new apb4_spi, new QSPIBundle(nss), true)

class TimerBundle extends Bundle {
  val exclk_i = Input(Bool())
  val capch_i = Input(Bool())
}
class TimerIO extends MyAPB4Bundle {
  val tmr = new TimerBundle
  val tmr_irq_o = Output(Bool())
  override def connect_extra(extra: Data): Unit = extra.asInstanceOf[TimerBundle] <> this.tmr
  override def connect_irq(irq_o: Bool): Unit = irq_o := tmr_irq_o
}
class apb4_tmr extends BlackBoxWithAPB4(new TimerIO)
class APB4Timer(address: Seq[AddressSet])(implicit p: Parameters) extends APB4DevBlackBox(address, () => new apb4_tmr, new TimerBundle, true)

class UARTBundle extends Bundle {
  val uart_rx_i = Input(Bool())
  val uart_tx_o = Output(Bool())
}
class UARTnewIO extends MyAPB4Bundle {
  val uart = new UARTBundle
  val uart_irq_o = Output(Bool())
  override def connect_extra(extra: Data): Unit = extra.asInstanceOf[UARTBundle] <> this.uart
  override def connect_irq(irq_o: Bool): Unit = irq_o := uart_irq_o
}
class apb4_uart extends BlackBoxWithAPB4(new UARTnewIO)
class APB4UART(address: Seq[AddressSet])(implicit p: Parameters) extends APB4DevBlackBox(address, () => new apb4_uart, new UARTBundle, true)

class WDGBundle extends Bundle {
  val rtc_clk_i = Input(Bool())
  val rst_o = Output(Bool())
}
class WDGIO extends MyAPB4Bundle {
  val wdg = new WDGBundle
  override def connect_extra(extra: Data): Unit = extra.asInstanceOf[WDGBundle] <> this.wdg
}
class apb4_wdg extends BlackBoxWithAPB4(new WDGIO)
class APB4WDG(address: Seq[AddressSet])(implicit p: Parameters) extends APB4DevBlackBox(address, () => new apb4_wdg, new WDGBundle)

class RCUBundle extends Bundle {
  val ext_lfosc_clk_i  = Input(Bool())
  val ext_hfosc_clk_i  = Input(Bool())
  val ext_audosc_clk_i = Input(Bool())
  val ext_rst_n_i      = Input(Bool())
  val wdt_rst_n_i      = Input(Bool())
  val pll_en_i         = Input(Bool())
  val clk_cfg_i        = Input(UInt(3.W))
  val core_sel_i       = Input(UInt(5.W))
  val core_sel_o       = Output(UInt(5.W))
  val clk_o            = Output(UInt(7.W))
  val rst_n_o          = Output(UInt(7.W))
}
class RCUIO extends MyAPB4Bundle {
  val rcu = new RCUBundle
  override def connect_extra(extra: Data): Unit = extra.asInstanceOf[RCUBundle] <> this.rcu
}
class apb4_rcu extends BlackBoxWithAPB4(new RCUIO)
class APB4RCU(address: Seq[AddressSet])(implicit p: Parameters) extends APB4DevBlackBox(address, () => new apb4_rcu, new RCUBundle)
