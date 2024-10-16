package io.github.arainko.ducktape.internal

import scala.annotation.tailrec

private[ducktape] trait PlanTraverser[A] {
  final def run(plan: Plan[Erroneous, Fallible], initial: A): A = {
    @tailrec
    def recurse(stack: List[Plan[Erroneous, Fallible]], accumulator: A): A =
      stack match {
        case head :: next =>
          head match {
            case plan: Plan.Upcast =>
              recurse(next, foldOver(plan, accumulator))
            case plan @ Plan.BetweenProducts(_, _, fieldPlans) =>
              recurse(fieldPlans.values.toList ::: next, foldOver(plan, accumulator))
            case plan @ Plan.BetweenProductTuple(_, _, plans) =>
              recurse(plans.toList ::: next, foldOver(plan, accumulator))
            case plan @ Plan.BetweenTupleProduct(_, _, plans) =>
              recurse(plans.values.toList ::: next, foldOver(plan, accumulator))
            case plan @ Plan.BetweenTuples(_, _, plans) =>
              recurse(plans.toList ::: next, foldOver(plan, accumulator))
            case plan @ Plan.BetweenTupleFunction(_, _, plans) =>
              recurse(plans.values.toList ::: next, foldOver(plan, accumulator))
            case plan @ Plan.BetweenCoproducts(_, _, casePlans) =>
              recurse(casePlans.toList ::: next, foldOver(plan, accumulator))
            case plan @ Plan.BetweenProductFunction(_, _, argPlans) =>
              recurse(argPlans.values.toList ::: next, foldOver(plan, accumulator))
            case p @ Plan.BetweenOptions(_, _, plan) =>
              recurse(plan :: next, foldOver(p, accumulator))
            case p @ Plan.BetweenNonOptionOption(_, _, plan) =>
              recurse(plan :: next, foldOver(p, accumulator))
            case p @ Plan.BetweenCollections(_, _, _, plan) =>
              recurse(plan :: next, foldOver(p, accumulator))
            case plan: Plan.BetweenSingletons =>
              recurse(next, foldOver(plan, accumulator))
            case plan: Plan.UserDefined[Fallible] =>
              recurse(next, foldOver(plan, accumulator))
            case plan: Plan.Derived[Fallible] =>
              recurse(next, foldOver(plan, accumulator))
            case plan: Plan.Configured[Fallible] =>
              recurse(next, foldOver(plan, accumulator))
            case plan: Plan.BetweenWrappedUnwrapped =>
              recurse(next, foldOver(plan, accumulator))
            case plan: Plan.BetweenUnwrappedWrapped =>
              recurse(next, foldOver(plan, accumulator))
            case plan @ Plan.BetweenFallibleNonFallible(_, _, elemPlan) =>
              recurse(elemPlan :: next, foldOver(plan, accumulator))
            case plan @ Plan.BetweenFallibles(_, _, _, elemPlan) =>
              recurse(elemPlan :: next, foldOver(plan, accumulator))
            case plan: Plan.Error =>
              recurse(next, foldOver(plan, accumulator))
          }
        case Nil => accumulator
      }

    recurse(plan :: Nil, initial)
  }

  protected def foldOver(plan: Plan[Erroneous, Fallible], accumulator: A): A
}
