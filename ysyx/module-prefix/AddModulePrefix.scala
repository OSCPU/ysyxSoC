package yourpackage  // modify to your package name

import firrtl._
import firrtl.annotations.NoTargetAnnotation
import firrtl.ir._
import firrtl.stage.TransformManager.TransformDependency

case class ModulePrefixAnnotation(prefix: String) extends NoTargetAnnotation

class AddModulePrefix extends Transform with DependencyAPIMigration {

  override def prerequisites: Seq[TransformDependency] = firrtl.stage.Forms.ChirrtlForm

  override protected def execute(state: CircuitState): CircuitState = {
    val c = state.circuit

    val prefix = state.annotations.collectFirst {
      case ModulePrefixAnnotation(p) => p
    }.get

    def onStmt(s: Statement): Statement = s match {
      case DefInstance(info, name, module, tpe) =>
        DefInstance(info, name, prefix + module, tpe)
      case other =>
        other.mapStmt(onStmt)
    }

    def onModule(m: DefModule): DefModule = {
      val newMod = m.mapStmt(onStmt)
      newMod match {
        case Module(info, name, ports, body) =>
          Module(info, prefix + name, ports, body)
        case ExtModule(info, name, ports, defname, params) =>
          ExtModule(info, prefix + name, ports, prefix + defname, params)
      }
    }
    val newCircuit = c.mapModule(onModule)
    state.copy(newCircuit.copy(main = prefix + newCircuit.main))
  }
}
