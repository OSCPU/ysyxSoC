package ysyx

import chisel3._
import chisel3.util._

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

  val luart0    = LazyModule(new APBUart16550(AddrSpace(0x10000000, 0x8)))
  val lspi      = LazyModule(new APBSPI      (AddrSpace(0x10001000, 0x20)   ++     // SPI controller
                                              AddrSpace(0x30000000, 0x10000000)))  // XIP flash
  val larchinfo = LazyModule(new APB4ArchInfo(AddrSpace(0x10006000, 0x10)))

//val lgpio     = LazyModule(new APBGPIO     (AddrSpace(0x10002000, 0x10)))
//val lkeyboard = LazyModule(new APBKeyboard (AddrSpace(0x10011000, 0x8)))
//val lvga      = LazyModule(new APBVGA      (AddrSpace(0x21000000, 0x200000)))
  val lpsram    = LazyModule(new APBPSRAM    (AddrSpace(0x80000000L, 0x400000)))

  List(lspi, luart0,
       larchinfo,
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

    // connect interrupt signal to cpu
    val intr = IO(Input(Bool()))
    cpu.module.interrupt := intr

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

    val uart  = genAPB4DevIO("uart", luart0)
    val spi   = genAPB4DevIO("spi", lspi)
    val psram = genAPB4DevIO("psram", lpsram)
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

    masic.intr := false.B

    val flash = Module(new flash)
    flash.io <> masic.spi
    flash.io.ss := masic.spi.ss(0)
    val bitrev = Module(new bitrev)
    bitrev.io <> masic.spi
    bitrev.io.ss := masic.spi.ss(7)
    masic.spi.miso := List(bitrev.io, flash.io).map(_.miso).reduce(_&&_)

    val psram = Module(new psram)
    psram.io <> masic.psram

    val externalPins = IO(new Bundle{
      //val gpio = chiselTypeOf(masic.gpio)
      //val ps2 = chiselTypeOf(masic.ps2)
      //val vga = chiselTypeOf(masic.vga)
      val uart = chiselTypeOf(masic.uart)
    })
    //externalPins.gpio <> masic.gpio
    //externalPins.ps2 <> masic.ps2
    //externalPins.vga <> masic.vga
    externalPins.uart <> masic.uart
  }
}
