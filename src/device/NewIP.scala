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
