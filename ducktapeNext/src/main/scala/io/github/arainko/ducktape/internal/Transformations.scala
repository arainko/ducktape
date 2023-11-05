package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.*
import scala.quoted.*
import scala.quoted.runtime.StopMacroExpansion

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
            ErrorMessage.CouldntCreateTransformationFromFunction(Span.fromExpr(function)),
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
        val ogErrors =
          plan.refine.swap
            .map(_.toList)
            .getOrElse(Nil)
            .filterNot(ogError =>
              ogError.message.target match
                case Target.Source =>
                  reconfiguredPlan.successfulConfigs.exists(succ => 
                    succ.target == Target.Source && succ.path.isAncestorOrSiblingOf(ogError.sourceContext)
                  )
                case Target.Dest =>
                  reconfiguredPlan.successfulConfigs.exists(succ =>
                    succ.target == Target.Dest && succ.path.isAncestorOrSiblingOf(ogError.destContext)
                  )
            )

          //  reconfiguredPlan.configErrors
        // val ogErrors = plan.refine.swap
        // .map(_.toList)
        //   .getOrElse(Nil)
        //   .filter(error =>
        //     errors.exists(err => err.destContext.isAncestorOrSiblingOf(error.destContext))
        //   ) // O(n^2), maybe there's a better way?
        Logger.debug("ORIGINAL ERRORS", plan.refine.swap.map(_.toList).getOrElse(Nil))
        Logger.debug("ERRORS", errors)
        Logger.debug("CONFIG ERRORS", reconfiguredPlan)
        Logger.debug("OG ERRORS", ogErrors)

        val allErrors = errors ::: reconfiguredPlan.configErrors ::: ogErrors
        val spanForAccumulatedErrors = Span.minimalAvailable(configs.map(_.span))
        allErrors.groupBy {
          _.message.span match
            case None       => spanForAccumulatedErrors
            case span: Span => span
        }
          .transform((_, errors) => errors.map(_.render).toList.distinct.mkString(System.lineSeparator))
          .foreach { (span, errorMessafe) => report.error(errorMessafe, span.toPosition) }

        throw new StopMacroExpansion
      case Right(totalPlan) =>
        PlanInterpreter.run[A](totalPlan, value)
    }
  }

  extension (self: Plan.Error)
    private def render(using Quotes) = {
      def renderSingle(error: Plan.Error)(using Quotes) = {
        val renderedPath =
          error.message.target match
            case Target.Source => error.sourceContext.render
            case Target.Dest   => error.destContext.render

        s"${error.message.render} @ $renderedPath"
      }

      def ident(times: Int) = "  " * times

      val suppressedErrors =
        List
          .unfold(self)(_.suppressed.map(suppressedErr => suppressedErr -> suppressedErr))
          .zipWithIndex
          .map((err, depth) =>
            s"SUPPRESSES: ${renderSingle(err)}".linesWithSeparators.map(line => ident(depth + 1) + line).mkString
          )

      String.join(System.lineSeparator, (renderSingle(self) :: suppressedErrors)*)
    }
}
