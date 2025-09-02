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
  val irq_o = Output(Bool())
}
class GPIOnewIO extends MyAPB4Bundle {
  val gpio = new GPIOBundle
  override def connect_extra(extra: Data): Unit = extra.asInstanceOf[GPIOBundle] <> this.gpio
}
class apb4_gpio extends BlackBoxWithAPB4(new GPIOnewIO)
class APB4GPIO(address: Seq[AddressSet])(implicit p: Parameters) extends APB4DevBlackBox(address, () => new apb4_gpio, new GPIOBundle)

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
  val irq_o = Output(Bool())
}
class I2CIO extends MyAPB4Bundle {
  val i2c = new I2CBundle
  override def connect_extra(extra: Data): Unit = extra.asInstanceOf[I2CBundle] <> this.i2c
}
class apb4_i2c extends BlackBoxWithAPB4(new I2CIO)
class APB4I2C(address: Seq[AddressSet])(implicit p: Parameters) extends APB4DevBlackBox(address, () => new apb4_i2c, new I2CBundle)

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
  val irq_o = Output(Bool())
}
class I2SIO extends MyAPB4Bundle {
  val i2s = new I2SBundle
  override def connect_extra(extra: Data): Unit = extra.asInstanceOf[I2SBundle] <> this.i2s
}
class apb4_i2s extends BlackBoxWithAPB4(new I2SIO)
class APB4I2S(address: Seq[AddressSet])(implicit p: Parameters) extends APB4DevBlackBox(address, () => new apb4_i2s, new I2SBundle)

class TimerBundle extends Bundle {
  val exclk_i = Input(Bool())
  val capch_i = Input(Bool())
  val irq_o = Output(Bool())
}
class TimerIO extends MyAPB4Bundle {
  val tmr = new TimerBundle
  override def connect_extra(extra: Data): Unit = extra.asInstanceOf[TimerBundle] <> this.tmr
}
class apb4_tmr extends BlackBoxWithAPB4(new TimerIO)
class APB4Timer(address: Seq[AddressSet])(implicit p: Parameters) extends APB4DevBlackBox(address, () => new apb4_tmr, new TimerBundle)
