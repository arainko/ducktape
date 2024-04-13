package io.github.arainko.ducktape.internal

private[ducktape] object PlanRefiner {
  private val errorCollector =
    new PlanTraverser[List[Plan.Error]] {
      protected def foldOver(plan: Plan[Plan.Error, Fallible], accumulator: List[Plan.Error]): List[Plan.Error] =
        plan match {
          case error: Plan.Error => error :: accumulator
          case other             => accumulator
        }
    }

  def run[F <: Fallible](plan: Plan[Plan.Error, F]): Either[NonEmptyList[Plan.Error], Plan[Nothing, F]] = {
    val errors = errorCollector.run(plan, Nil)
    // if no errors were accumulated that means there are no Plan.Error nodes which means we operate on a Plan[Nothing]
    NonEmptyList.fromList(errors).toLeft(plan.asInstanceOf[Plan[Nothing, F]])
  }
}
