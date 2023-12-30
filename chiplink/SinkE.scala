// See LICENSE for license details.
package sifive.blocks.devices.chiplink

import chisel3._
import chisel3.util._
import freechips.rocketchip.tilelink._

class SinkE(info: ChipLinkInfo) extends Module
{
  val io = IO(new Bundle {
    val e = Flipped(Decoupled(new TLBundleE(info.edgeIn.bundle)))
    val q = Decoupled(new DataLayer(info.params))
    // Find the sink from D
    val d_tlSink = Valid(UInt(info.params.sinkBits.W))
    val d_clSink = Input(UInt(info.params.clSinkBits.W))
  })

  io.d_tlSink.valid := io.e.fire
  io.d_tlSink.bits := io.e.bits.sink

  val header = info.encode(
    format = 4.U,
    opcode = 0.U,
    param  = 0.U,
    size   = 0.U,
    domain = 0.U,
    source = io.d_clSink)

  io.e.ready := io.q.ready
  io.q.valid := io.e.valid
  io.q.bits.last  := true.B
  io.q.bits.data  := header
  io.q.bits.beats := 1.U
}
