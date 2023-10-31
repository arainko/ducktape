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
            Some(Span.fromExpr(function))
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
    Logger.info("Reconfigured plan", reconfiguredPlan.result)
    
    reconfiguredPlan.result.refine match {
      case Left(errors) =>
        Logger.info("All errors", errors.map(_.message))
        val ogErrors = 
          plan
            .refine
            .swap
            .getOrElse(Nil)
            .filter(error => errors.exists(err => err.destContext.isAncestorOrSiblingOf(error.destContext))) // O(n^2), maybe there's a better way?

        val allErrors = errors ::: reconfiguredPlan.configErrors ::: ogErrors

        val spanForAccumulatedErrors = Span.minimalAvailable(configs.map(_.span))
        Logger.info(s"Accumulated span", spanForAccumulatedErrors)
        val accumulatedErrors =
          allErrors
            .filter(_.span.isEmpty)
            .map(err => s"${err.message} @ ${err.sourceContext.render}")
            .mkString("\n") + "HAHAHA"

        allErrors.collect {
          case Plan.Error(_, _, sourceContext, _, message, Some(span)) =>
            Logger.info("Span", span)
            report.error(s"$message @ ${sourceContext.render}", span.toPosition)
        }

        // Logger.info("Fun", spanForAccumulatedErrors.toPosition.sourceCode)
            
        report.errorAndAbort(accumulatedErrors, spanForAccumulatedErrors.toPosition)
      case Right(totalPlan) =>
        PlanInterpreter.run[A](totalPlan, value)
    }
  }
}
