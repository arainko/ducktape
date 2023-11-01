package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.*
import scala.quoted.*

object Transformations {
  inline def between[A, B](
    value: A,
    inline configs: Field[A, B] | Case[A, B]*
  ): B = ${ createTransformationBetween[A, B]('value, 'configs) }

  private def createTransformationBetween[A: Type, B: Type](
    value: Expr[A],
    configs: Expr[Seq[Field[A, B] | Case[A, B]]]
  )(using Quotes): Expr[B] = {
    import quotes.reflect.*

    val plan = Planner.betweenTypes[A, B]
    val config = Configuration.parse(configs)
    createTransformation(value, plan, config).asExprOf[B]
  }

  transparent inline def via[A, Func, Args <: FunctionArguments](
    value: A,
    inline function: Func,
    inline configs: Field[A, Args] | Case[A, Args]*
  ): Any = ${ createTransformationVia('value, 'function, 'configs) }

  private def createTransformationVia[A: Type, Func: Type, Args <: FunctionArguments: Type](
    value: Expr[A],
    function: Expr[Func],
    configs: Expr[Seq[Field[A, Args] | Case[A, Args]]]
  )(using Quotes) = {
    import quotes.reflect.*

    val plan =
      Function
        .fromFunctionArguments[Args, Func](function)
        .orElse(Function.fromExpr(function))
        .map(Planner.betweenTypeAndFunction[A])
        .getOrElse(
          Plan.Error(
            Type.of[A],
            Type.of[Any],
            Path.empty(Type.of[A]),
            Path.empty(Type.of[Any]),
            "Couldn't create a transformation plan from a function",
            Some(Span.fromExpr(function)),
            None
          )
        )

    val config = Configuration.parse(configs)
    createTransformation(value, plan, config)
  }

  private def createTransformation[A: Type](
    value: Expr[A],
    plan: Plan[Plan.Error],
    configs: List[Configuration.At]
  )(using Quotes) = {
    import quotes.reflect.*

    val reconfiguredPlan = plan.configureAll(configs)

    Logger.debug("Original plan", plan)
    Logger.debug("Config", configs)
    Logger.debug("Reconfigured plan", reconfiguredPlan)

    reconfiguredPlan.result.refine match {
      case Left(errors) =>
        val ogErrors = plan.refine.swap.map(_.toList).getOrElse(Nil)
        val allErrors = errors ::: reconfiguredPlan.configErrors ::: ogErrors
        val spanForAccumulatedErrors = Span.minimalAvailable(configs.map(_.span))
        val groupedBySpan =
          allErrors
            .groupBy(_.span.getOrElse(spanForAccumulatedErrors))
            .transform((_, errors) => errors.map(_.render).toList.distinct.mkString("\n"))
            
        groupedBySpan.tail.foreach { (span, errorMessafe) => report.error(errorMessafe, span.toPosition) }
        val (finalErrorSpan, finalErrorMessage) = groupedBySpan.head
        report.errorAndAbort(finalErrorMessage, finalErrorSpan.toPosition)
      case Right(totalPlan) =>
        PlanInterpreter.run[A](totalPlan, value)
    }
  }

  extension (self: Plan.Error) private def render(using Quotes) = {
    val suppressedErrors = 
      List.unfold(self)(_.suppressed.map(suppressedErr => suppressedErr -> suppressedErr))

    Logger.debug("dupsko", suppressedErrors)

    s"${self.message} @ ${self.sourceContext.render} " + Debug.show(suppressedErrors)
  }
}
