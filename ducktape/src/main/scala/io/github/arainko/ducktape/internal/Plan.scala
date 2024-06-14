package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.internal.*

import scala.collection.immutable.ListMap
import scala.quoted.*
import scala.reflect.TypeTest

private[ducktape] object Fallible
private[ducktape] type Fallible = Fallible.type

private[ducktape] sealed trait Plan[+E <: Plan.Error, +F <: Fallible] {
  import Plan.*

  def source: Structure

  def dest: Structure

  final def sourcePath: Path = source.path

  final def destPath: Path = dest.path

  final def narrow[A <: Plan[Plan.Error, Fallible]](using tt: TypeTest[Plan[Plan.Error, Fallible], A]): Option[A] =
    tt.unapply(this)

  final def configureAll[FF >: F <: Fallible](configs: List[Configuration.Instruction[FF]])(using Quotes): Plan.Reconfigured[FF] =
    PlanConfigurer.run(this, configs)

  final def refine: Either[NonEmptyList[Plan.Error], Plan[Nothing, F]] = PlanRefiner.run(this)
}

private[ducktape] object Plan {
  case class Upcast(
    source: Structure,
    dest: Structure
  ) extends Plan[Nothing, Nothing]

  case class UserDefined[+F <: Fallible](
    source: Structure,
    dest: Structure,
    transformer: Summoner.UserDefined[F]
  ) extends Plan[Nothing, F]

  case class Derived[+F <: Fallible](
    source: Structure,
    dest: Structure,
    transformer: Summoner.Derived[F]
  ) extends Plan[Nothing, F]

  case class Configured[+F <: Fallible] private (
    source: Structure,
    dest: Structure,
    config: Configuration[F],
    span: Span
  ) extends Plan[Nothing, F]

  case class BetweenProductFunction[+E <: Plan.Error, +F <: Fallible](
    source: Structure.Product,
    dest: Structure.Function,
    argPlans: ListMap[String, Plan[E, F]]
  ) extends Plan[E, F]

  case class BetweenTupleFunction[+E <: Plan.Error, +F <: Fallible](
    source: Structure.Tuple,
    dest: Structure.Function,
    argPlans: Vector[Plan[E, F]]
  ) extends Plan[E, F]

  case class BetweenUnwrappedWrapped(
    source: Structure,
    dest: Structure.ValueClass
  ) extends Plan[Nothing, Nothing]

  case class BetweenWrappedUnwrapped(
    source: Structure.ValueClass,
    dest: Structure,
    fieldName: String
  ) extends Plan[Nothing, Nothing]

  case class BetweenSingletons(
    source: Structure.Singleton,
    dest: Structure.Singleton
  ) extends Plan[Nothing, Nothing]

  case class BetweenProducts[+E <: Plan.Error, +F <: Fallible](
    source: Structure.Product,
    dest: Structure.Product,
    fieldPlans: Map[String, Plan[E, F]]
  ) extends Plan[E, F]

  case class BetweenProductTuple[+E <: Plan.Error, +F <: Fallible](
    source: Structure.Product,
    dest: Structure.Tuple,
    plans: Vector[Plan[E, F]]
  ) extends Plan[E, F]

  case class BetweenTupleProduct[+E <: Plan.Error, +F <: Fallible](
    source: Structure.Tuple,
    dest: Structure.Product,
    plans: Vector[Plan[E, F]]
  ) extends Plan[E, F]

  case class BetweenTuples[+E <: Plan.Error, +F <: Fallible](
    source: Structure.Tuple,
    dest: Structure.Tuple,
    plans: Vector[Plan[E, F]]
  ) extends Plan[E, F]

  case class BetweenCoproducts[+E <: Plan.Error, +F <: Fallible](
    source: Structure.Coproduct,
    dest: Structure.Coproduct,
    casePlans: Vector[Plan[E, F]]
  ) extends Plan[E, F]

  case class BetweenOptions[+E <: Plan.Error, +F <: Fallible](
    source: Structure.Optional,
    dest: Structure.Optional,
    plan: Plan[E, F]
  ) extends Plan[E, F]

  case class BetweenNonOptionOption[+E <: Plan.Error, +F <: Fallible](
    source: Structure,
    dest: Structure.Optional,
    plan: Plan[E, F]
  ) extends Plan[E, F]

  case class BetweenCollections[+E <: Plan.Error, +F <: Fallible](
    source: Structure.Collection,
    dest: Structure.Collection,
    plan: Plan[E, F]
  ) extends Plan[E, F]

  case class Error(
    source: Structure,
    dest: Structure,
    message: ErrorMessage,
    suppressed: Option[Plan.Error]
  ) extends Plan[Plan.Error, Nothing]

  object Error {
    def from(plan: Plan[Plan.Error, Fallible], message: ErrorMessage, suppressed: Option[Plan.Error]): Plan.Error =
      Plan.Error(
        plan.source,
        plan.dest,
        message,
        suppressed
      )
  }

  object Configured {
    def from[F <: Fallible](plan: Plan[Plan.Error, F], conf: Configuration[F], instruction: Configuration.Instruction[F])(using
      Quotes
    ): Plan.Configured[F] =
      (plan.source.tpe, plan.dest.tpe, conf.tpe) match {
        case ('[src], '[dest], '[confTpe]) =>
          val source = if instruction.side.isDest then Structure.Lazy.of[confTpe](plan.source.path) else plan.source
          val dest = if instruction.side.isSource then Structure.Lazy.of[confTpe](plan.dest.path) else plan.dest
          Plan.Configured(source, dest, conf, instruction.span)
      }
  }

  given debug: Debug[Plan[Plan.Error, Fallible]] = Debug.derived

  final case class Reconfigured[+F <: Fallible](
    errors: List[Plan.Error],
    successes: List[(Path, Side)],
    warnings: List[ConfigWarning],
    result: Plan[Plan.Error, F]
  )

  object Reconfigured {
    given debug: Debug[Reconfigured[Fallible]] = Debug.derived
  }
}
