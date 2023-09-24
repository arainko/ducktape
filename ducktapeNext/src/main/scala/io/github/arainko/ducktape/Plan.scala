package io.github.arainko.ducktape

import scala.quoted.*
import io.github.arainko.ducktape.internal.modules.*
import io.github.arainko.ducktape.internal.Debug
import scala.collection.IterableFactory
import scala.collection.Factory
import Plan.Error as PlanError //TODO: Why is this needed? I cannot refer to Plan.Error in bound of the Plan enum, lol
import io.github.arainko.ducktape.Path.Segment
import scala.collection.immutable.ListMap
import scala.annotation.tailrec
import io.github.arainko.ducktape.Configuration.At
type PlanError = Plan.Error

enum Plan[+E <: PlanError] {
  import Plan.*

  def sourceTpe: Type[?]

  def destTpe: Type[?]

  def sourceContext: Plan.Context

  def destContext: Plan.Context

  final def configure(config: Configuration.At)(using Quotes): Plan[Plan.Error] = Plan.configure(this, config)

  final def refine: Either[List[Plan.Error], Plan[Nothing]] = Plan.refine(this)

  case Upcast(
    sourceTpe: Type[?],
    destTpe: Type[?],
    sourceContext: Plan.Context,
    destContext: Plan.Context
  ) extends Plan[Nothing]

  case UserDefined(
    sourceTpe: Type[?],
    destTpe: Type[?],
    sourceContext: Plan.Context,
    destContext: Plan.Context,
    transformer: Expr[UserDefinedTransformer[?, ?]]
  ) extends Plan[Nothing]

  case Derived(
    sourceTpe: Type[?],
    destTpe: Type[?],
    sourceContext: Plan.Context,
    destContext: Plan.Context,
    transformer: Expr[Transformer2[?, ?]]
  ) extends Plan[Nothing]

  case Configured(
    sourceTpe: Type[?],
    destTpe: Type[?],
    sourceContext: Plan.Context,
    destContext: Plan.Context,
    config: Configuration
  ) extends Plan[Nothing]

  case BetweenProductFunction(
    sourceTpe: Type[?],
    destTpe: Type[?],
    sourceContext: Plan.Context,
    destContext: Plan.Context,
    argPlans: ListMap[String, Plan[E]],
    function: Function
  ) extends Plan[E]

  case BetweenUnwrappedWrapped(
    sourceTpe: Type[?],
    destTpe: Type[?],
    sourceContext: Plan.Context,
    destContext: Plan.Context
  ) extends Plan[Nothing]

  case BetweenWrappedUnwrapped(
    sourceTpe: Type[?],
    destTpe: Type[?],
    sourceContext: Plan.Context,
    destContext: Plan.Context,
    fieldName: String
  ) extends Plan[Nothing]

  case BetweenSingletons(
    sourceTpe: Type[?],
    destTpe: Type[?],
    sourceContext: Plan.Context,
    destContext: Plan.Context,
    expr: Expr[Any]
  ) extends Plan[Nothing]

  case BetweenProducts(
    sourceTpe: Type[?],
    destTpe: Type[?],
    sourceContext: Plan.Context,
    destContext: Plan.Context,
    fieldPlans: Map[String, Plan[E]]
  ) extends Plan[E]

  case BetweenCoproducts(
    sourceTpe: Type[?],
    destTpe: Type[?],
    sourceContext: Plan.Context,
    destContext: Plan.Context,
    casePlans: Vector[Plan[E]]
  ) extends Plan[E]

  case BetweenOptions(
    sourceTpe: Type[?],
    destTpe: Type[?],
    sourceContext: Plan.Context,
    destContext: Plan.Context,
    plan: Plan[E]
  ) extends Plan[E]

  case BetweenNonOptionOption(
    sourceTpe: Type[?],
    destTpe: Type[?],
    sourceContext: Plan.Context,
    destContext: Plan.Context,
    plan: Plan[E]
  ) extends Plan[E]

  case BetweenCollections(
    destCollectionTpe: Type[?],
    sourceTpe: Type[?],
    destTpe: Type[?],
    sourceContext: Plan.Context,
    destContext: Plan.Context,
    plan: Plan[E]
  ) extends Plan[E]

  // TODO: Use a well typed error definition here and not a String
  case Error(
    sourceTpe: Type[?],
    destTpe: Type[?],
    sourceContext: Plan.Context,
    destContext: Plan.Context,
    message: String
  ) extends Plan[Plan.Error]
}

object Plan {

  def unapply[E <: Plan.Error](plan: Plan[E]): (Type[?], Type[?]) = (plan.sourceTpe, plan.destTpe)

  given debug: Debug[Plan[?]] = Debug.derived

  final case class Context(root: Type[?], path: Path) derives Debug {
    def add(segment: Path.Segment): Context = copy(path = path.appended(segment))

    def currentTpe(using Quotes): Type[?] = {
      import quotes.reflect.*

      path.toVector.reverse.collectFirst { case Segment.Field(tpe, name) => tpe }
        .getOrElse(root)
        .repr
        .widen
        .asType
    }

    def render(using Quotes): String = path.render
  }

  object Context {
    def empty(root: Type[?]): Context = Context(root, Path.empty)
  }

  private def configure[E <: Plan.Error](plan: Plan[E], config: Configuration.At)(using Quotes): Plan[Plan.Error] = {
    extension (currentPlan: Plan[?]) {
      def conformsTo(update: Configuration.At)(using Quotes) = {
        import quotes.reflect.*

        update match {
          case Configuration.At(path, target, Configuration.Const(value)) =>
            value.asTerm.tpe <:< currentPlan.destContext.currentTpe.repr
          case Configuration.At(path, target, Configuration.Computed(destTpe, function)) => 
            destTpe.repr <:< currentPlan.destContext.currentTpe.repr
        }
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
            case plan =>
              Plan.Error(
                plan.sourceTpe,
                plan.destTpe,
                plan.sourceContext,
                plan.destContext,
                s"A field accessor can only be used to configure product or function transformations"
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
        case Nil if current.conformsTo(config) =>
          Plan.Configured(current.sourceTpe, current.destTpe, current.sourceContext, current.destContext, config.config)

        case Nil =>
          Plan.Error(
            current.sourceTpe,
            current.destTpe,
            current.sourceContext,
            current.destContext,
            s"A replacement plan doesn't conform to the plan it's supposed to replace"
          )
      }
    }

    recurse(plan, config.path.toList)
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
