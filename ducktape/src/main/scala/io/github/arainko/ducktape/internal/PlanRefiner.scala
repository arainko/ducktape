package io.github.arainko.ducktape.internal

import scala.annotation.tailrec

private[ducktape] object PlanRefiner {
  def run[F <: Fallible](plan: Plan[Plan.Error, F]): Either[NonEmptyList[Plan.Error], Plan[Nothing, F]] = {

    @tailrec
    def recurse(stack: List[Plan[Plan.Error, F]], errors: List[Plan.Error]): List[Plan.Error] =
      stack match {
        case head :: next =>
          head match {
            case plan: Plan.Upcast => recurse(next, errors)
            case Plan.BetweenProducts(_, _, fieldPlans) =>
              recurse(fieldPlans.values.toList ::: next, errors)
            case Plan.BetweenCoproducts(_, _, casePlans) =>
              recurse(casePlans.toList ::: next, errors)
            case Plan.BetweenProductFunction(_, _, argPlans) =>
              recurse(argPlans.values.toList ::: next, errors)
            case Plan.BetweenOptions(_, _, plan)         => recurse(plan :: next, errors)
            case Plan.BetweenNonOptionOption(_, _, plan) => recurse(plan :: next, errors)
            case Plan.BetweenCollections(_, _, plan)     => recurse(plan :: next, errors)
            case plan: Plan.BetweenSingletons            => recurse(next, errors)
            case plan: Plan.UserDefined                  => recurse(next, errors)
            case plan: Plan.Derived                      => recurse(next, errors)
            case plan: Plan.Configured[F]                => recurse(next, errors)
            case plan: Plan.BetweenWrappedUnwrapped      => recurse(next, errors)
            case plan: Plan.BetweenUnwrappedWrapped      => recurse(next, errors)
            case error: Plan.Error                       => recurse(next, error :: errors)
          }
        case Nil => errors
      }
    val errors = recurse(plan :: Nil, Nil)
    // if no errors were accumulated that means there are no Plan.Error nodes which means we operate on a Plan[Nothing]
    NonEmptyList.fromList(errors).toLeft(plan.asInstanceOf[Plan[Nothing, F]])
  }
}
