package ysyx

import chisel3._
import chisel3.util.log2Up

import org.chipsalliance.cde.config.{Parameters, Config}
import freechips.rocketchip.system._
import freechips.rocketchip.diplomacy.LazyModule

object Config {
  def ysyxStage = 'C'
  def hasHomework: Boolean = true

  require(ysyxStage >= 'A' && ysyxStage <= 'F')
  def isMPSoC: Boolean = (ysyxStage == 'F')
  def isMini: Boolean = (ysyxStage >= 'D') && hasHomework  // mini SoC for learning
  def isSimpleBus: Boolean = (ysyxStage >= 'D')
  def hasPLL: Boolean = (ysyxStage <= 'C')
  def hasMoreHomework: Boolean = (ysyxStage <= 'C') && hasHomework
  def hasIntr: Boolean = (ysyxStage == 'A')
  def hasCDC: Boolean = (ysyxStage <= 'C') || hasHomework

  def numCore: Int = 1         // for SoC templates
  def isCPUDataBits64 = false  // not for ysyx
  def idBits: Int = 4

  def numDataPAD = 73
  def coreSelWidth = log2Up(numCore)

  // for MPSoC
  def numInnerDataPAD = 64
  def tileSelWidth = numDataPAD - numInnerDataPAD - 2 // 2 for clock and reset
  def numTile: Int = scala.math.pow(2, tileSelWidth).toInt
}

class ElaborateTop extends Module {
  implicit val config: Parameters = new Config(new Edge32BitConfig ++ new DefaultRV32Config)

  val io = IO(new Bundle { })
  if (!Config.isMPSoC) {
    val dut = LazyModule(new SimTop)
    val mdut = Module(dut.module)
    mdut.dontTouchPorts()
    mdut.externalPins := DontCare
    mdut.cpuClock := DontCare
    mdut.coreSel := DontCare
  } else {
    val dut = Module(new MPSoCasicTop)
    dut.clock_pad_i := clock
    dut.resetn_pad_i := ~reset.asBool
    dut.dontTouchPorts()
    dut.tileSel := DontCare
    dut.dip := DontCare
    dut.btn := DontCare
    dut.customIn := DontCare
  }
}

object Elaborate extends App {
  val firtoolLoweringOptions = Array("--lowering-options=" + List(
    // make yosys happy
    // see https://github.com/llvm/circt/blob/main/docs/VerilogGeneration.md
    "disallowLocalVariables",
    "disallowPackedArrays",
    "locationInfoStyle=wrapInAtSquareBracket",
//    "disallowExpressionInliningInPorts"
  ).reduce(_ + "," + _))

  val firtoolOptions = // Array("--preserve-aggregate=none", "--preserve-values=strip") ++
                       firtoolLoweringOptions ++ Array("--disable-annotation-unknown")
  circt.stage.ChiselStage.emitSystemVerilogFile(new ElaborateTop, args, firtoolOptions)
}
