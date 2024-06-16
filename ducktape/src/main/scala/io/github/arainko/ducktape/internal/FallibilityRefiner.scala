package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.internal.Plan.*
import io.github.arainko.ducktape.internal.Summoner.Derived.{ FallibleTransformer, TotalTransformer }
import io.github.arainko.ducktape.internal.Summoner.UserDefined.{ FallibleTransformer, TotalTransformer }

import scala.util.boundary
import scala.util.boundary.Label

private[ducktape] object FallibilityRefiner {
  def run[E <: Plan.Error](plan: Plan[E, Fallible]): Plan[E, Nothing] | None.type =
    recurse(plan) match
      case None    => None
      case b: Unit => plan.asInstanceOf[Plan[E, Nothing]]

  private def recurse[E <: Plan.Error](plan: Plan[E, Fallible]): None.type | Unit =
    boundary[None.type | Unit]:
      plan match
        case Upcast(source, dest) => ()

        case UserDefined(source, dest, transformer) =>
          transformer match
            case Summoner.UserDefined.TotalTransformer(value)    => ()
            case Summoner.UserDefined.FallibleTransformer(value) => boundary.break(None)

        case Derived(source, dest, transformer) =>
          transformer match
            case Summoner.Derived.TotalTransformer(value)    => ()
            case Summoner.Derived.FallibleTransformer(value) => boundary.break(None)

        case Configured(source, dest, config, _) =>
          config match
            case Configuration.Const(value, tpe)                    => ()
            case Configuration.CaseComputed(tpe, function)          => ()
            case Configuration.FieldComputed(tpe, function)         => ()
            case Configuration.FieldReplacement(source, name, tpe)  => ()
            case Configuration.FallibleConst(value, tpe)            => boundary.break(None)
            case Configuration.FallibleFieldComputed(tpe, function) => boundary.break(None)
            case Configuration.FallibleCaseComputed(tpe, function)  => boundary.break(None)

        case BetweenProductFunction(source, dest, argPlans) =>
          evaluate(argPlans.values)

        case BetweenTupleFunction(source, dest, argPlans) => 
          evaluate(argPlans.values)

        case BetweenUnwrappedWrapped(source, dest) => ()

        case BetweenWrappedUnwrapped(source, dest, fieldName) => ()

        case BetweenSingletons(source, dest) => ()

        case BetweenProducts(source, dest, fieldPlans) =>
          evaluate(fieldPlans.values)

        case BetweenProductTuple(source, dest, plans) =>
          evaluate(plans)

        case BetweenTupleProduct(source, dest, plans) => 
          evaluate(plans.values)

        case BetweenTuples(source, dest, plans) => 
          evaluate(plans)

        case BetweenCoproducts(source, dest, casePlans) =>
          evaluate(casePlans)

        case BetweenOptions(source, dest, plan) =>
          recurse(plan)

        case BetweenNonOptionOption(source, dest, plan) =>
          recurse(plan)

        case BetweenCollections(source, dest, plan) =>
          recurse(plan)

        case Plan.Error(source, dest, message, suppressed) => ()

  private inline def evaluate(plans: Iterable[Plan[Plan.Error, Fallible]])(using inline label: boundary.Label[None.type | Unit]) =
    val iterator = plans.iterator
    while iterator.hasNext do
      recurse(iterator.next()) match {
        case None => boundary.break(None)
        case ()   => ()
      }
}
