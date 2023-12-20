package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.internal.*

import scala.collection.immutable.ListMap
import scala.quoted.*

private[ducktape] sealed trait Plan[+E <: Plan.Error] {
  import Plan.*

  def sourceTpe: Type[?]

  def destTpe: Type[?]

  def sourceContext: Path

  def destContext: Path

  final def configureAll(configs: List[Configuration.At])(using Quotes): Plan.Reconfigured = PlanConfigurer.run(this, configs)

  final def refine: Either[NonEmptyList[Plan.Error], Plan[Nothing]] = PlanRefiner.run(this)
}

private[ducktape] object Plan {
  case class Upcast(
    sourceTpe: Type[?],
    destTpe: Type[?],
    sourceContext: Path,
    destContext: Path
  ) extends Plan[Nothing]

  case class UserDefined(
    sourceTpe: Type[?],
    destTpe: Type[?],
    sourceContext: Path,
    destContext: Path,
    transformer: Expr[Transformer[?, ?]]
  ) extends Plan[Nothing]

  case class Derived(
    sourceTpe: Type[?],
    destTpe: Type[?],
    sourceContext: Path,
    destContext: Path,
    transformer: Expr[Transformer.Derived[?, ?]]
  ) extends Plan[Nothing]

  case class Configured(
    sourceTpe: Type[?],
    destTpe: Type[?],
    sourceContext: Path,
    destContext: Path,
    config: Configuration
  ) extends Plan[Nothing]

  case class BetweenProductFunction[+E <: Plan.Error](
    sourceTpe: Type[?],
    destTpe: Type[?],
    sourceContext: Path,
    destContext: Path,
    argPlans: ListMap[String, Plan[E]],
    function: Function
  ) extends Plan[E]

  case class BetweenUnwrappedWrapped(
    sourceTpe: Type[?],
    destTpe: Type[?],
    sourceContext: Path,
    destContext: Path
  ) extends Plan[Nothing]

  case class BetweenWrappedUnwrapped(
    sourceTpe: Type[?],
    destTpe: Type[?],
    sourceContext: Path,
    destContext: Path,
    fieldName: String
  ) extends Plan[Nothing]

  case class BetweenSingletons(
    sourceTpe: Type[?],
    destTpe: Type[?],
    sourceContext: Path,
    destContext: Path,
    expr: Expr[Any]
  ) extends Plan[Nothing]

  case class BetweenProducts[+E <: Plan.Error](
    sourceTpe: Type[?],
    destTpe: Type[?],
    sourceContext: Path,
    destContext: Path,
    fieldPlans: Map[String, Plan[E]]
  ) extends Plan[E]

  case class BetweenCoproducts[+E <: Plan.Error](
    sourceTpe: Type[?],
    destTpe: Type[?],
    sourceContext: Path,
    destContext: Path,
    casePlans: Vector[Plan[E]]
  ) extends Plan[E]

  case class BetweenOptions[+E <: Plan.Error](
    sourceTpe: Type[?],
    destTpe: Type[?],
    sourceContext: Path,
    destContext: Path,
    plan: Plan[E]
  ) extends Plan[E]

  case class BetweenNonOptionOption[+E <: Plan.Error](
    sourceTpe: Type[?],
    destTpe: Type[?],
    sourceContext: Path,
    destContext: Path,
    plan: Plan[E]
  ) extends Plan[E]

  case class BetweenCollections[+E <: Plan.Error](
    destCollectionTpe: Type[? <: Iterable[?]],
    sourceTpe: Type[?],
    destTpe: Type[?],
    sourceContext: Path,
    destContext: Path,
    plan: Plan[E]
  ) extends Plan[E]

  case class Error(
    sourceTpe: Type[?],
    destTpe: Type[?],
    sourceContext: Path,
    destContext: Path,
    message: ErrorMessage,
    suppressed: Option[Plan.Error]
  ) extends Plan[Plan.Error]

  object Error {
    def from(plan: Plan[Plan.Error], message: ErrorMessage, suppressed: Option[Plan.Error]): Plan.Error =
      Plan.Error(
        plan.sourceTpe,
        plan.destTpe,
        plan.sourceContext,
        plan.destContext,
        message,
        suppressed
      )
  }

  def unapply[E <: Plan.Error](plan: Plan[E]): (Type[?], Type[?]) = (plan.sourceTpe, plan.destTpe)

  given debug[E <: Plan.Error]: Debug[Plan[E]] = Debug.derived

  final case class Reconfigured(
    errors: List[Plan.Error],
    successes: List[Configuration.At.Successful],
    result: Plan[Plan.Error]
  ) derives Debug
}
