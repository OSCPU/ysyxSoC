package ysyx

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy._

class apb4_archinfo extends BlackBoxWithAPB4
class APB4ArchInfo(address: Seq[AddressSet])(implicit p: Parameters) extends APB4DevBlackBox(address, () => new apb4_archinfo)
