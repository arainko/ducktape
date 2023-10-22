package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.internal.*

import scala.annotation.tailrec
import scala.collection.immutable.ListMap
import scala.collection.{ Factory, IterableFactory }
import scala.quoted.*

type PlanError = Plan.Error

enum Plan[+E <: PlanError] {
  import Plan.*

  def sourceTpe: Type[?]

  def destTpe: Type[?]

  def sourceContext: Path

  def destContext: Path

  final def configure(config: Configuration.At)(using Quotes): Plan[Plan.Error] = Plan.configure(this, config)

  final def refine: Either[List[Plan.Error], Plan[Nothing]] = Plan.refine(this)

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
    destCollectionTpe: Type[?],
    sourceTpe: Type[?],
    destTpe: Type[?],
    sourceContext: Path,
    destContext: Path,
    plan: Plan[E]
  ) extends Plan[E]

  // TODO: Use a well typed error definition here and not a String
  case Error(
    sourceTpe: Type[?],
    destTpe: Type[?],
    sourceContext: Path,
    destContext: Path,
    message: String
  ) extends Plan[Plan.Error]
}

object Plan {

  def unapply[E <: Plan.Error](plan: Plan[E]): (Type[?], Type[?]) = (plan.sourceTpe, plan.destTpe)

  given debug[E <: Plan.Error]: Debug[Plan[E]] = Debug.derived

  private def configure[E <: Plan.Error](plan: Plan[E], config: Configuration.At)(using Quotes): Plan[Plan.Error] = {
    extension (currentPlan: Plan[?]) {
      def conformsTo(update: Configuration.At.Successful)(using Quotes) = {
        update.config.tpe.repr <:< currentPlan.destContext.currentTpe.repr
      }
    }

    def recurse(
      current: Plan[E],
      segments: List[Path.Segment]
    )(using Quotes): Plan[Plan.Error] = {
      segments match {
        case (segment @ Path.Segment.Field(_, fieldName)) :: tail =>
          current match {
            case plan @ BetweenProducts(sourceTpe, destTpe, sourceContext, destContext, fieldPlans) =>
              val fieldPlan =
                fieldPlans
                  .get(fieldName)
                  .map(fieldPlan => recurse(fieldPlan, tail))
                  .getOrElse(
                    Plan.Error(sourceTpe, destTpe, sourceContext, destContext, s"'$fieldName' is not a valid field accessor")
                  )

              plan.copy(fieldPlans = fieldPlans.updated(fieldName, fieldPlan))
            case plan @ BetweenProductFunction(sourceTpe, destTpe, sourceContext, destContext, argPlans, function) =>
              val argPlan =
                argPlans
                  .get(fieldName)
                  .map(argPlan => recurse(argPlan, tail))
                  .getOrElse(
                    Plan.Error(sourceTpe, destTpe, sourceContext, destContext, s"'$fieldName' is not a valid arg accessor")
                  )

              plan.copy(argPlans = argPlans.updated(fieldName, argPlan))
            case plan => // TODO: Somehow keep around information if plan is as Plan.Error so that the error message is nicer
              Plan.Error(
                plan.sourceTpe,
                plan.destTpe,
                plan.sourceContext,
                plan.destContext,
                s"A field accessor '$fieldName' can only be used to configure product or function transformations"
              )
          }

        case (segment @ Path.Segment.Case(tpe)) :: tail =>
          current match {
            case plan @ BetweenCoproducts(sourceTpe, destTpe, sourceContext, destContext, casePlans) =>
              def targetTpe(plan: Plan[E]) = if (config.target.isSource) plan.sourceTpe.repr else plan.destTpe.repr

              casePlans.zipWithIndex
                .find((plan, idx) => tpe.repr =:= targetTpe(plan))
                .map((casePlan, idx) => plan.copy(casePlans = casePlans.updated(idx, recurse(casePlan, tail))))
                .getOrElse(
                  Plan
                    .Error(sourceTpe, destTpe, sourceContext, destContext, s"'at[${tpe.repr.show}]' is not a valid case accessor")
                )
            case plan =>
              Plan.Error(
                plan.sourceTpe,
                plan.destTpe,
                plan.sourceContext,
                plan.destContext,
                s"A case accessor can only be used to configure coproduct transformations"
              )
          }

        case Nil =>
          config match {
            case cfg @ Configuration.At.Successful(_, _, config) if current.conformsTo(cfg) =>
              Plan.Configured(current.sourceTpe, current.destTpe, current.sourceContext, current.destContext, config)

            case Configuration.At.Successful(path, target, config) =>
              Plan.Error(
                current.sourceTpe,
                current.destTpe,
                current.sourceContext,
                current.destContext,
                s"A replacement plan doesn't conform to the plan it's supposed to replace. ${config.tpe.repr.show} <:< ${current.destContext.currentTpe.repr.show}"
              )

            case Configuration.At.Failed(path, target, message) =>
              Plan.Error(
                current.sourceTpe,
                current.destTpe,
                current.sourceContext,
                current.destContext,
                message
              )
          }
      }
    }

    recurse(plan, config.path.segments.toList)
  }

  private def refine(plan: Plan[Plan.Error]): Either[List[Plan.Error], Plan[Nothing]] = {

    @tailrec
    def recurse(stack: List[Plan[Plan.Error]], errors: List[Plan.Error]): List[Plan.Error] =
      stack match {
        case head :: next =>
          head match {
            case plan: Plan.Upcast => recurse(next, errors)
            case Plan.BetweenProducts(_, _, _, _, fieldPlans) =>
              recurse(fieldPlans.values.toList ::: next, errors)
            case Plan.BetweenCoproducts(_, _, _, _, casePlans) =>
              recurse(casePlans.toList ::: next, errors)
            case Plan.BetweenProductFunction(_, _, _, _, argPlans, _) =>
              recurse(argPlans.values.toList ::: next, errors)
            case Plan.BetweenOptions(_, _, _, _, plan)         => recurse(plan :: next, errors)
            case Plan.BetweenNonOptionOption(_, _, _, _, plan) => recurse(plan :: next, errors)
            case Plan.BetweenCollections(_, _, _, _, _, plan)  => recurse(plan :: next, errors)
            case plan: Plan.BetweenSingletons                  => recurse(next, errors)
            case plan: Plan.UserDefined                        => recurse(next, errors)
            case plan: Plan.Derived                            => recurse(next, errors)
            case plan: Plan.Configured                         => recurse(next, errors)
            case plan: Plan.BetweenWrappedUnwrapped            => recurse(next, errors)
            case plan: Plan.BetweenUnwrappedWrapped            => recurse(next, errors)
            case error: Plan.Error                             => recurse(next, error :: errors)
          }
        case Nil => errors
      }
    val errors = recurse(plan :: Nil, Nil)
    // if no errors were accumulated that means there are no Plan.Error nodes which means we operate on a Plan[Nothing]
    Either.cond(errors.isEmpty, plan.asInstanceOf[Plan[Nothing]], errors)
  }
}
