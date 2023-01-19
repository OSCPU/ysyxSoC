package ysyx

import chisel3._

import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._

class VGAIO extends Bundle {
    val hsync     = Output(Bool())
    val vsync     = Output(Bool())
    val vga_r     = Output(UInt(4.W))
    val vga_g     = Output(UInt(4.W))
    val vga_b     = Output(UInt(4.W))
}

class vga_ctrl extends BlackBox {
    val io = IO(new Bundle{
        val clock     = Input(Clock())
        val resetn    = Input(Bool())
        val io_master = AXI4Bundle(CPUAXI4BundleParameters())
        val io_slave  = Flipped(new AXI4Bundle(CPUAXI4BundleParameters()))
        val hsync     = Output(Bool())
        val vsync     = Output(Bool())
        val vga_r     = Output(UInt(4.W))
        val vga_g     = Output(UInt(4.W))
        val vga_b     = Output(UInt(4.W))
    })
}

class screen extends BlackBox {
    // val io = IO(Flipped(new VGAIO))
    val io = IO(new Bundle{
        val clock = Input(Clock())
        val dat   = Flipped(new VGAIO)
    })
}

class AXI4VGA(idBits: Int, address: Seq[AddressSet])(implicit p: Parameters) extends LazyModule {
  val node = AXI4SlaveNode(Seq(AXI4SlavePortParameters(
    Seq(AXI4SlaveParameters(
      address       = address,
      executable    = false,
      supportsRead  = TransferSizes(1, 8),
      supportsWrite = TransferSizes.none)),
    beatBytes  = 8))) // for support same beatBytes xbar impl

//   val masterNode = AXI4MasterNode(p(ExtIn).map(params =>
    // AXI4MasterPortParameters(
    //   masters = Seq(AXI4MasterParameters(
        // name = "vga",
        // id   = IdRange(0, 1 << idBits))))).toSeq)

    lazy val module = new LazyModuleImp(this) {
        val (in, _) = node.in(0)
        // val (master, _) = masterNode.out(0)
        val io = IO(new VGAIO)
        val mvga_ctrl = Module(new vga_ctrl)
        mvga_ctrl.io.clock  := clock;
        mvga_ctrl.io.resetn := ~reset.asBool
        // master <> mvga_ctrl.io.io_master
        mvga_ctrl.io.io_slave <> in
        mvga_ctrl.io.hsync <> io.hsync
        mvga_ctrl.io.vsync <> io.vsync
        mvga_ctrl.io.vga_r <> io.vga_r
        mvga_ctrl.io.vga_g <> io.vga_g
        mvga_ctrl.io.vga_b <> io.vga_b
    }
}
