package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.internal.*

import scala.collection.immutable.ListMap
import scala.quoted.*
import scala.reflect.TypeTest

private[ducktape] sealed trait Plan[+E <: Plan.Error] {
  import Plan.*

  def source: Structure

  def dest: Structure

  final def sourcePath: Path = source.path

  final def destPath: Path = dest.path

  final def narrow[A <: Plan[Plan.Error]](using tt: TypeTest[Plan[Plan.Error], A]): Option[A] = tt.unapply(this)

  final def configureAll(configs: List[Configuration.Instruction])(using Quotes): Plan.Reconfigured =
    PlanConfigurer.run(this, configs)

  final def refine: Either[NonEmptyList[Plan.Error], Plan[Nothing]] = PlanRefiner.run(this)
}

private[ducktape] object Plan {
  case class Upcast(
    source: Structure,
    dest: Structure
  ) extends Plan[Nothing]

  case class UserDefined(
    source: Structure,
    dest: Structure,
    transformer: Expr[Transformer[?, ?]]
  ) extends Plan[Nothing]

  case class Derived(
    source: Structure,
    dest: Structure,
    transformer: Expr[Transformer.Derived[?, ?]]
  ) extends Plan[Nothing]

  case class Configured(
    source: Structure,
    dest: Structure,
    config: Configuration
  ) extends Plan[Nothing]

  case class BetweenProductFunction[+E <: Plan.Error](
    source: Structure.Product,
    dest: Structure.Function,
    argPlans: ListMap[String, Plan[E]]
  ) extends Plan[E]

  case class BetweenUnwrappedWrapped(
    source: Structure,
    dest: Structure.ValueClass
  ) extends Plan[Nothing]

  case class BetweenWrappedUnwrapped(
    source: Structure.ValueClass,
    dest: Structure,
    fieldName: String
  ) extends Plan[Nothing]

  case class BetweenSingletons(
    source: Structure.Singleton,
    dest: Structure.Singleton
  ) extends Plan[Nothing]

  case class BetweenProducts[+E <: Plan.Error](
    source: Structure.Product,
    dest: Structure.Product,
    fieldPlans: Map[String, Plan[E]]
  ) extends Plan[E]

  case class BetweenCoproducts[+E <: Plan.Error](
    source: Structure.Coproduct,
    dest: Structure.Coproduct,
    casePlans: Vector[Plan[E]]
  ) extends Plan[E]

  case class BetweenOptions[+E <: Plan.Error](
    source: Structure.Optional,
    dest: Structure.Optional,
    plan: Plan[E]
  ) extends Plan[E]

  case class BetweenNonOptionOption[+E <: Plan.Error](
    source: Structure,
    dest: Structure.Optional,
    plan: Plan[E]
  ) extends Plan[E]

  case class BetweenCollections[+E <: Plan.Error](
    source: Structure.Collection,
    dest: Structure.Collection,
    plan: Plan[E]
  ) extends Plan[E]

  case class Error(
    source: Structure,
    dest: Structure,
    message: ErrorMessage,
    suppressed: Option[Plan.Error]
  ) extends Plan[Plan.Error]

  object Error {
    def from(plan: Plan[Plan.Error], message: ErrorMessage, suppressed: Option[Plan.Error]): Plan.Error =
      Plan.Error(
        plan.source,
        plan.dest,
        message,
        suppressed
      )
  }

  object Configured {
    def from(plan: Plan[Plan.Error], conf: Configuration): Plan.Configured =
      Plan.Configured(
        plan.source,
        plan.dest,
        conf
      )
  }

  given debug: Debug[Plan[Plan.Error]] = Debug.derived

  final case class Reconfigured(
    errors: List[Plan.Error],
    successes: List[(Path, Side)],
    result: Plan[Plan.Error]
  ) derives Debug
}
