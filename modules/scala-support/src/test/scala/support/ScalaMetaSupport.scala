package support

import org.scalactic.Equality
import org.scalatest.matchers._
import scala.meta._

trait ScalaMetaMatchers {
  implicit def TreeEquality[A <: Tree]: Equality[A] =
    new Equality[A] {
      def areEqual(a: A, b: Any): Boolean =
        b match {
          case x: Tree => a.structure == x.structure
          case _       => false
        }
    }

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

  class StructureMatcher(expectedStructure: Tree) extends Matcher[Tree] {
    def apply(left: Tree) =
      MatchResult(
        normalizeImplicits(expectedStructure).structure == normalizeImplicits(left).structure,
        s"""${expectedStructure.syntax} did not equal ${left.syntax}""",
        s"""Failure message"""
      )
  }

  def matchStructure(expectedStructure: Tree) = new StructureMatcher(expectedStructure)
}
