package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.internal.*

import scala.collection.immutable.ListMap
import scala.quoted.*

private[ducktape] type PlanError = Plan.Error

private[ducktape] enum Plan[+E <: PlanError] {
  import Plan.*

  def sourceTpe: Type[?]

  def destTpe: Type[?]

  def sourceContext: Path

  def destContext: Path

  final def configureAll(configs: List[Configuration.At])(using Quotes): Plan.Reconfigured = PlanConfigurer.run(this, configs)

  final def refine: Either[NonEmptyList[Plan.Error], Plan[Nothing]] = PlanRefiner.run(this)

  case Upcast(
    sourceTpe: Type[?],
    destTpe: Type[?],
    sourceContext: Path,
    destContext: Path
  ) extends Plan[Nothing]

  case UserDefined(
    sourceTpe: Type[?],
    destTpe: Type[?],
    sourceContext: Path,
    destContext: Path,
    transformer: Expr[Transformer[?, ?]]
  ) extends Plan[Nothing]

  case Derived(
    sourceTpe: Type[?],
    destTpe: Type[?],
    sourceContext: Path,
    destContext: Path,
    transformer: Expr[Transformer.Derived[?, ?]]
  ) extends Plan[Nothing]

  case Configured(
    sourceTpe: Type[?],
    destTpe: Type[?],
    sourceContext: Path,
    destContext: Path,
    config: Configuration
  ) extends Plan[Nothing]

  case BetweenProductFunction(
    sourceTpe: Type[?],
    destTpe: Type[?],
    sourceContext: Path,
    destContext: Path,
    argPlans: ListMap[String, Plan[E]],
    function: Function
  ) extends Plan[E]

  case BetweenUnwrappedWrapped(
    sourceTpe: Type[?],
    destTpe: Type[?],
    sourceContext: Path,
    destContext: Path
  ) extends Plan[Nothing]

  case BetweenWrappedUnwrapped(
    sourceTpe: Type[?],
    destTpe: Type[?],
    sourceContext: Path,
    destContext: Path,
    fieldName: String
  ) extends Plan[Nothing]

  case BetweenSingletons(
    sourceTpe: Type[?],
    destTpe: Type[?],
    sourceContext: Path,
    destContext: Path,
    expr: Expr[Any]
  ) extends Plan[Nothing]

  case BetweenProducts(
    sourceTpe: Type[?],
    destTpe: Type[?],
    sourceContext: Path,
    destContext: Path,
    fieldPlans: Map[String, Plan[E]]
  ) extends Plan[E]

  case BetweenCoproducts(
    sourceTpe: Type[?],
    destTpe: Type[?],
    sourceContext: Path,
    destContext: Path,
    casePlans: Vector[Plan[E]]
  ) extends Plan[E]

  case BetweenOptions(
    sourceTpe: Type[?],
    destTpe: Type[?],
    sourceContext: Path,
    destContext: Path,
    plan: Plan[E]
  ) extends Plan[E]

  case BetweenNonOptionOption(
    sourceTpe: Type[?],
    destTpe: Type[?],
    sourceContext: Path,
    destContext: Path,
    plan: Plan[E]
  ) extends Plan[E]

  case BetweenCollections(
    destCollectionTpe: Type[? <: Iterable[?]],
    sourceTpe: Type[?],
    destTpe: Type[?],
    sourceContext: Path,
    destContext: Path,
    plan: Plan[E]
  ) extends Plan[E]

  case Error(
    sourceTpe: Type[?],
    destTpe: Type[?],
    sourceContext: Path,
    destContext: Path,
    message: ErrorMessage,
    suppressed: Option[Plan.Error]
  ) extends Plan[Plan.Error]
}

private[ducktape] object Plan {

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
