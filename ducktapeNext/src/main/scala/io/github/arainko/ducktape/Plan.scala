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

  final def show(using Quotes): String = {
    import quotes.reflect.*
    given (using q: Quotes): q.reflect.Printer[q.reflect.TypeRepr] = q.reflect.Printer.TypeReprShortCode
    extension (tpe: Type[?])(using Quotes) def show: String = tpe.repr.show

    this match {
      case Upcast(sourceTpe, destTpe) =>
        s"Upcast(${sourceTpe.show}, ${destTpe.show})"
      case BetweenProducts(sourceTpe, destTpe, fieldPlans) =>
        s"BetweenProducts(${sourceTpe.show}, ${destTpe.show}, ${fieldPlans.map((name, plan) => name -> plan.show)})"
      case BetweenCoproducts(sourceTpe, destTpe, casePlans) =>
        s"BetweenCoproducts(${sourceTpe.show}, ${destTpe.show}, ${casePlans.map(_.show)})"
      case BetweenOptions(sourceTpe, destTpe, plan) =>
        s"BetweenOptions(${sourceTpe.show}, ${destTpe.show}, ${plan.show})"
      case BetweenNonOptionOption(sourceTpe, destTpe, plan) =>
        s"BetweenNonOptionOption(${sourceTpe.show}, ${destTpe.show}, ${plan.show})"
      case BetweenCollections(destCollectionTpe, sourceTpe, destTpe, plan) =>
        s"BetweenCollections(${destCollectionTpe.show}, ${sourceTpe.show}, ${destTpe.show}, ${plan.show})"
      case BetweenSingletons(sourceTpe, destTpe, expr) =>
        s"BetweenSingletons(${sourceTpe.show}, ${destTpe.show}, Expr(...))"
      case UserDefined(sourceTpe, destTpe, transformer) =>
        s"UserDefined(${sourceTpe.show}, ${destTpe.show}, Transformer[...])"
      case Derived(sourceTpe, destTpe, transformer) =>
        s"Derived(${sourceTpe.show}, ${destTpe.show}, Transformer[...])"
      case Configured(sourceTpe, destTpe, config) =>
        s"Configured(${sourceTpe.show}, ${destTpe.show}, $config)"
      case PlanError(sourceTpe, destTpe, context, message) =>
        s"Error(${sourceTpe.show}, ${destTpe.show}, ${context.render}, ${message})"
      case BetweenUnwrappedWrapped(sourceTpe, destTpe) =>
        s"BetweenUnwrappedWrapped(${sourceTpe.show}, ${destTpe.show})"
      case BetweenWrappedUnwrapped(sourceTpe, destTpe, fieldName) =>
        s"BetweenWrappedUnwrapped(${sourceTpe.show}, ${destTpe.show}, $fieldName)"
    }
  }

  final def replaceAt(
    path: Path,
    target: Configuration.Target
  )(update: Plan[Plan.Error])(using Quotes): Plan[Plan.Error] = {
    extension (currentPlan: Plan[?]) def conformsTo(updatedPlan: Plan[?])(using Quotes) = {
        import quotes.reflect.*

        // val noise = defn
        // // uncomment when you once again hit an issue with this to see what's happening
        // println(s"updated.destTpe: ${updatedPlan.destTpe.repr.show}")
        // println(s"current.destTpe: ${currentPlan.destTpe.repr.show}")
        // println(s"current derivesFrom updated: ${currentPlan.destTpe.repr.derivesFrom(updatedPlan.destTpe.repr.typeSymbol)}")
        // println(s"updated derivesFrom current: ${updatedPlan.destTpe.repr.derivesFrom(currentPlan.destTpe.repr.typeSymbol)}")
        // println(s"updated <:< current: ${updatedPlan.destTpe.repr <:< currentPlan.destTpe.repr}")
        // println(s"current <:< updated: ${currentPlan.destTpe.repr <:< updatedPlan.destTpe.repr}")
        // println(s"updated baseType current: ${updatedPlan.destTpe.repr.baseType(currentPlan.destTpe.repr.typeSymbol)}")
        // println(s"current baseType updated: ${currentPlan.destTpe.repr.baseType(updatedPlan.destTpe.repr.typeSymbol)}")
        // println(s"current.baseClasses: ${currentPlan.destTpe.repr.baseClasses}")
        // println(s"updated.baseClasses: ${updatedPlan.destTpe.repr.baseClasses}")
        // println(s"updated.classSymbol: ${updatedPlan.destTpe.repr.classSymbol}")
        // println(s"current.classSymbol: ${currentPlan.destTpe.repr.classSymbol}")
        // println(
        //   s"intersection: ${currentPlan.destTpe.repr.baseClasses.toSet.intersect(updatedPlan.destTpe.repr.baseClasses.toSet).filter(_.flags.is(Flags.Sealed)).map(_.typeRef)}"
        // )
        // println(s"updated.filtered: ${updatedPlan.destTpe.repr.baseClasses.filter(_.flags.is(Flags.Sealed))}")
        // println(s"current.filtered: ${currentPlan.destTpe.repr.baseClasses.filter(_.flags.is(Flags.Sealed))}")

        // println(s"updated baseType current: ${updatedPlan.destTpe.repr.baseType(currentPlan.destTpe.repr.typeSymbol)}")
        // println(s"current baseType updated: ${currentPlan.destTpe.repr.baseType(updatedPlan.destTpe.repr.typeSymbol)}")

        // val lubs = currentPlan.destTpe.repr.baseClasses.toSet
        //   .intersect(updatedPlan.destTpe.repr.baseClasses.toSet)
        //   .filter(_.flags.is(Flags.Sealed))
        //   .map(_.typeRef)

        updatedPlan.sourceTpe.repr <:< currentPlan.sourceTpe.repr &&
        (updatedPlan.destTpe.repr <:< currentPlan.destTpe.repr || currentPlan.destTpe.repr.derivesFrom(
          updatedPlan.destTpe.repr.typeSymbol
        ))
      }

    def recurse(
      current: Plan[E],
      segments: List[Path.Segment],
      sourceContext: Plan.Context,
      destContext: Plan.Context
    )(using Quotes): Plan[Plan.Error] = {
      segments match {
        case (segment @ Path.Segment.Field(_, fieldName)) :: tail =>
          val updatedContext = context.add(segment)

          current match {
            case plan @ BetweenProducts(sourceTpe, destTpe, fieldPlans) =>
              val fieldPlan =
                fieldPlans
                  .get(fieldName)
                  .map(fieldPlan => recurse(fieldPlan, tail, updatedContext))
                  .getOrElse(Plan.Error(sourceTpe, destTpe, updatedContext, s"'$fieldName' is not a valid field accessor"))

              plan.copy(fieldPlans = fieldPlans.updated(fieldName, fieldPlan))
            case plan =>
              Plan.Error(
                plan.sourceTpe,
                plan.destTpe,
                updatedContext,
                s"A field accessor can only be used to configure product transformations"
              )
          }

        case (segment @ Path.Segment.Case(tpe)) :: tail =>
          val updatedContext = context.add(segment)

          current match {
            case plan @ BetweenCoproducts(sourceTpe, destTpe, casePlans) =>
              def targetTpe(plan: Plan[E]) = if (target.isSource) plan.sourceTpe.repr else plan.destTpe.repr

              casePlans.zipWithIndex
                .find((plan, idx) => tpe.repr =:= targetTpe(plan))
                .map((casePlan, idx) => plan.copy(casePlans = casePlans.updated(idx, recurse(casePlan, tail, updatedContext))))
                .getOrElse(Plan.Error(sourceTpe, destTpe, updatedContext, s"'at[${tpe.repr.show}]' is not a valid case accessor"))
            case plan =>
              Plan.Error(
                plan.sourceTpe,
                plan.destTpe,
                updatedContext,
                s"A case accessor can only be used to configure coproduct transformations"
              )
          }
        case Nil if current.conformsTo(update) =>
          println(context.render)

          update

        case Nil =>
          println(context.render)

          Plan.Error(
            current.sourceTpe,
            current.destTpe,
            context,
            s"A replacement plan doesn't conform to the plan it's supposed to replace"
          )
      }
    }

    recurse(this, path.toList, Context.empty(this.sourceTpe))
  }

  case Upcast(sourceTpe: Type[?], destTpe: Type[?]) extends Plan[Nothing]
  case UserDefined(sourceTpe: Type[?], destTpe: Type[?], transformer: Expr[UserDefinedTransformer[?, ?]]) extends Plan[Nothing]
  case Derived(sourceTpe: Type[?], destTpe: Type[?], transformer: Expr[Transformer2[?, ?]]) extends Plan[Nothing]
  case Configured(sourceTpe: Type[?], destTpe: Type[?], config: Configuration) extends Plan[Nothing]
  case BetweenUnwrappedWrapped(sourceTpe: Type[?], destTpe: Type[?]) extends Plan[Nothing]
  case BetweenWrappedUnwrapped(sourceTpe: Type[?], destTpe: Type[?], fieldName: String) extends Plan[Nothing]
  case BetweenSingletons(sourceTpe: Type[?], destTpe: Type[?], expr: Expr[Any]) extends Plan[Nothing]
  case BetweenProducts(sourceTpe: Type[?], destTpe: Type[?], fieldPlans: Map[String, Plan[E]]) extends Plan[E]
  case BetweenCoproducts(sourceTpe: Type[?], destTpe: Type[?], casePlans: Vector[Plan[E]]) extends Plan[E]
  case BetweenOptions(sourceTpe: Type[?], destTpe: Type[?], plan: Plan[E]) extends Plan[E]
  case BetweenNonOptionOption(sourceTpe: Type[?], destTpe: Type[?], plan: Plan[E]) extends Plan[E]
  case BetweenCollections(destCollectionTpe: Type[?], sourceTpe: Type[?], destTpe: Type[?], plan: Plan[E]) extends Plan[E]
  // TODO: Use a well typed error definition here and not a String
  case Error(sourceTpe: Type[?], destTpe: Type[?], context: Plan.Context, message: String) extends Plan[Plan.Error]
}

object Plan {

  def unapply[E <: Plan.Error](plan: Plan[E]): (Type[?], Type[?]) = (plan.sourceTpe, plan.destTpe)

  final case class Context(root: Type[?], path: Path) {
    def add(segment: Path.Segment): Context = copy(path = path.appended(segment))

    // def currentDestTpe(using Quotes): Type[?] = {
    //   import quotes.reflect.*

    //   path.toVector.reverse.collectFirst { case Segment.Field(tpe, name) => tpe }
    //     .getOrElse(rootDestTpe)
    //     .repr
    //     .widen
    //     .asType
    // }

    def render(using Quotes): String = path.render
  // }
  }

  object Context {
    def empty(root: Type[?]): Context = Context(root, Path.empty)
  }
}