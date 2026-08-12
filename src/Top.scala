package ysyx

import chisel3._
import chisel3.util.log2Up

import org.chipsalliance.cde.config.{Parameters, Config}
import freechips.rocketchip.system._
import freechips.rocketchip.diplomacy.LazyModule

object Config {
  def idBits: Int = 4
  def isMini: Boolean = true     // mini SoC for learning
  def isSimpleBus: Boolean = false
  def numCore: Int = 1
  def hasIntr: Boolean = false
  def hasHomework: Boolean = true
  def hasMoreHomework: Boolean = false

  def isCPUDataBits64 = false

  def coreSelWidth = log2Up(numCore)
}

class ElaborateTop extends Module {
  implicit val config: Parameters = new Config(new Edge32BitConfig ++ new DefaultRV32Config)

  val io = IO(new Bundle { })
  val dut = LazyModule(new SimTop)
  val mdut = Module(dut.module)
  mdut.dontTouchPorts()
  mdut.externalPins := DontCare
  mdut.coreSel := DontCare
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
