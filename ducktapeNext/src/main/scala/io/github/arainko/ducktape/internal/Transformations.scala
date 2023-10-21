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
            Plan.Context.empty(Type.of[A]),
            Plan.Context.empty(Type.of[Any]),
            "Couldn't create a transformation plan from a function"
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

    val reconfiguredPlan = configs.foldLeft(plan) { (plan, config) => plan.configure(config) }

    Logger.debug("Original plan", plan)
    Logger.debug("Config", configs)
    Logger.debug("Reconfigured plan", reconfiguredPlan)

    reconfiguredPlan.refine match {
      case Left(errors) =>
        val rendered = errors.map(err => s"${err.message} @ ${err.sourceContext.render}").mkString("\n")
        report.errorAndAbort(rendered)
      case Right(totalPlan) =>
        PlanInterpreter.run[A](totalPlan, value)
    }
  }
}
