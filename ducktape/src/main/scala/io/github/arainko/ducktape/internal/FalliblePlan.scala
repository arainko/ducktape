package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.Transformer.Fallible
import scala.quoted.Expr
import io.github.arainko.ducktape.Transformer

sealed trait FalliblePlan[+E <: FalliblePlan.Error] {
  def source: Structure
  def dest: Structure
  
  def sourcePath: Path
  def destPath: Path
}

object FalliblePlan {

  case class Total(plan: Plan[Nothing]) extends FalliblePlan[Nothing] {
    export plan.{source, dest, sourcePath as sourcePath, destPath as destPath}
  }

  case class UserDefined(
    source: Structure,
    dest: Structure,
    sourcePath: Path,
    destPath: Path,
    transformer: Expr[Transformer.Fallible[?, ?, ?]]
  ) extends FalliblePlan[Nothing]

  case class BetweenProducts[+E <: FalliblePlan.Error](
    source: Structure.Product,
    dest: Structure.Product,
    sourcePath: Path,
    destPath: Path,
    fieldPlans: Map[String, FalliblePlan[E]]
  ) extends FalliblePlan[E]

  case class BetweenCoproducts[+E <: FalliblePlan.Error](
    source: Structure.Coproduct,
    dest: Structure.Coproduct,
    sourcePath: Path,
    destPath: Path,
    casePlans: Map[String, FalliblePlan[E]]
  ) extends FalliblePlan[E]

  case class Error(
    source: Structure,
    dest: Structure,
    sourcePath: Path,
    destPath: Path,
    message: ErrorMessage
  ) extends FalliblePlan[FalliblePlan.Error]

}
