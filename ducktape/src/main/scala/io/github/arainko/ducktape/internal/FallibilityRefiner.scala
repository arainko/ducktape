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

        case Configured(source, dest, config) =>
          config match
            case Configuration.Const(value, tpe)                    => ()
            case Configuration.CaseComputed(tpe, function)          => ()
            case Configuration.FieldComputed(tpe, function)         => ()
            case Configuration.FieldReplacement(source, name, tpe)  => ()
            case Configuration.FallibleConst(value, tpe)            => boundary.break(None)
            case Configuration.FallibleFieldComputed(tpe, function) => boundary.break(None)
            case Configuration.FallibleCaseComputed(tpe, function)  => boundary.break(None)

        case BetweenProductFunction(source, dest, argPlans) =>
          val iterator = argPlans.valuesIterator
          while iterator.hasNext do
            recurse(iterator.next()) match {
              case None => boundary.break(None)
              case ()   => ()
            }

        case BetweenUnwrappedWrapped(source, dest) => ()

        case BetweenWrappedUnwrapped(source, dest, fieldName) => ()

        case BetweenSingletons(source, dest) => ()

        case BetweenProducts(source, dest, fieldPlans) =>
          val iterator = fieldPlans.valuesIterator
          while iterator.hasNext do
            recurse(iterator.next()) match {
              case None => boundary.break(None)
              case ()   => ()
            }

        case BetweenCoproducts(source, dest, casePlans) =>
          val iterator = casePlans.iterator
          while iterator.hasNext do
            recurse(iterator.next()) match {
              case None => boundary.break(None)
              case ()   => ()
            }

        case BetweenOptions(source, dest, plan) =>
          recurse(plan)

        case BetweenNonOptionOption(source, dest, plan) =>
          recurse(plan)

        case BetweenCollections(source, dest, plan) =>
          recurse(plan)

        case Plan.Error(source, dest, message, suppressed) => ()

}
