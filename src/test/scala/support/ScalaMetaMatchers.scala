package support

import scala.meta._
import org.scalatest.matchers._

trait ScalaMetaMatchers {
  // Quasiquotes parsed by scalameta >= 4.6 put Mod.Implicit on every parameter of an implicit clause as well as on the
  // clause itself, while programmatically-built trees only carry it on the clause. Normalize to the clause-level form.
  private def normalizeImplicits(tree: Tree): Tree =
    tree.transform {
      case clause @ Term.ParamClause(params, Some(_: Mod.Implicit)) =>
        Term.ParamClause(
          params.map(p => Term.Param(p.mods.filterNot(_.isInstanceOf[Mod.Implicit]), p.name, p.decltpe, p.default)),
          clause.mod
        )
    }

  class StructureMatcher(right: Tree) extends Matcher[Tree] {
    def apply(left: Tree): MatchResult =
      MatchResult(
        normalizeImplicits(left).structure == normalizeImplicits(right).structure,
        s"""$left did not match $right""",
        s"""$left matched $right"""
      )
  }

  def matchStructure(right: Tree): StructureMatcher =
    new StructureMatcher(right)
}

object ScalaMetaMatchers extends ScalaMetaMatchers
