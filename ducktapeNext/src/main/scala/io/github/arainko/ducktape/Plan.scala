package io.github.arainko.ducktape

import scala.quoted.*
import io.github.arainko.ducktape.internal.modules.*
import scala.collection.IterableFactory
import scala.collection.Factory
import Plan.Error as PlanError //TODO: Why is this needed? I cannot refer to Plan.Error in bound of the Plan enum, lol
import io.github.arainko.ducktape.Path.Segment
type PlanError = Plan.Error

enum Plan[+E <: PlanError] {
  import Plan.*

  def sourceTpe: Type[?]

  def destTpe: Type[?]

  def sourceContext: Plan.Context

  def destContext: Plan.Context

  final def show(using Quotes): String = {
    import quotes.reflect.*
    given (using q: Quotes): q.reflect.Printer[q.reflect.TypeRepr] = q.reflect.Printer.TypeReprShortCode
    extension (tpe: Type[?])(using Quotes) def show: String = tpe.repr.show

    this match {
      case Upcast(sourceTpe, destTpe, sourceContext, destContext) =>
        s"Upcast(${sourceTpe.show}, ${destTpe.show}, ${sourceContext.render}, ${destContext.render})"
      case BetweenProducts(sourceTpe, destTpe, sourceContext, destContext, fieldPlans) =>
        s"BetweenProducts(${sourceTpe.show}, ${destTpe.show}, ${sourceContext.render}, ${destContext.render}, ${fieldPlans.map((name, plan) => name -> plan.show)})"
      case BetweenCoproducts(sourceTpe, destTpe, sourceContext, destContext, casePlans) =>
        s"BetweenCoproducts(${sourceTpe.show}, ${destTpe.show}, ${sourceContext.render}, ${destContext.render}, ${casePlans.map(_.show)})"
      case BetweenOptions(sourceTpe, destTpe, sourceContext, destContext, plan) =>
        s"BetweenOptions(${sourceTpe.show}, ${destTpe.show}, ${sourceContext.render}, ${destContext.render}, ${plan.show})"
      case BetweenNonOptionOption(sourceTpe, destTpe, sourceContext, destContext, plan) =>
        s"BetweenNonOptionOption(${sourceTpe.show}, ${destTpe.show}, ${sourceContext.render}, ${destContext.render}, ${plan.show})"
      case BetweenCollections(destCollectionTpe, sourceTpe, destTpe, sourceContext, destContext, plan) =>
        s"BetweenCollections(${destCollectionTpe.show}, ${sourceTpe.show}, ${sourceContext.render}, ${destContext.render}, ${destTpe.show}, ${plan.show})"
      case BetweenSingletons(sourceTpe, destTpe, sourceContext, destContext, expr) =>
        s"BetweenSingletons(${sourceTpe.show}, ${destTpe.show}, ${sourceContext.render}, ${destContext.render}, Expr(...))"
      case UserDefined(sourceTpe, destTpe, sourceContext, destContext, transformer) =>
        s"UserDefined(${sourceTpe.show}, ${destTpe.show}, ${sourceContext.render}, ${destContext.render}, Transformer[...])"
      case Derived(sourceTpe, destTpe, sourceContext, destContext, transformer) =>
        s"Derived(${sourceTpe.show}, ${destTpe.show}, ${sourceContext.render}, ${destContext.render}, Transformer[...])"
      case Configured(sourceTpe, destTpe, sourceContext, destContext, config) =>
        s"Configured(${sourceTpe.show}, ${destTpe.show}, ${sourceContext.render}, ${destContext.render}, $config)"
      case PlanError(sourceTpe, destTpe, sourceContext, destContext, message) =>
        s"Error(${sourceTpe.show}, ${destTpe.show}, ${sourceContext.render}, ${destContext.render}, ${message})"
      case BetweenUnwrappedWrapped(sourceTpe, destTpe, sourceContext, destContext) =>
        s"BetweenUnwrappedWrapped(${sourceTpe.show}, ${destTpe.show}, ${sourceContext.render}, ${destContext.render})"
      case BetweenWrappedUnwrapped(sourceTpe, destTpe, sourceContext, destContext, fieldName) =>
        s"BetweenWrappedUnwrapped(${sourceTpe.show}, ${destTpe.show}, ${sourceContext.render}, ${destContext.render}, $fieldName)"
    }
  }

  final def configure(config: Configuration.At)(using Quotes): Plan[Plan.Error] = {
    extension (currentPlan: Plan[?]) def conformsTo(update: Configuration.At)(using Quotes) = {
        import quotes.reflect.*

        update match {
          case Configuration.At(path, target, Configuration.Const(value)) =>
            value.asTerm.tpe <:< currentPlan.destContext.currentTpe.repr
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
            case plan =>
              Plan.Error(
                plan.sourceTpe,
                plan.destTpe,
                sourceContext,
                destContext,
                s"A field accessor can only be used to configure product transformations"
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

    recurse(this, config.path.toList)
  }

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

  final case class Context(root: Type[?], path: Path) {
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
}
