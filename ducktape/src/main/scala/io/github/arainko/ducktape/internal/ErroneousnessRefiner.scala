package io.github.arainko.ducktape.internal

private[ducktape] object ErroneousnessRefiner {
  private object ErrorCollector extends PlanTraverser[List[Plan.Error]] {
    protected def foldOver(plan: Plan[Erroneous, Fallible], accumulator: List[Plan.Error]): List[Plan.Error] =
      plan match {
        case error: Plan.Error => error :: accumulator
        case other             => accumulator
      }
  }

  def run[F <: Fallible](plan: Plan[Erroneous, F]): Either[NonEmptyList[Plan.Error], Plan[Nothing, F]] = {
    // if no errors were accumulated that means there are no Plan.Error nodes which means we operate on a Plan[Nothing]
    NonEmptyList
      .fromList(ErrorCollector.run(plan, Nil))
      .toLeft(plan.asInstanceOf[Plan[Nothing, F]])
  }
}
