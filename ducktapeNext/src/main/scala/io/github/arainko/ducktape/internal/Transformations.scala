package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.internal.Function.fromFunctionArguments

import scala.quoted.*
import scala.quoted.runtime.StopMacroExpansion

private[ducktape] object Transformations {
  inline def between[A, B](
    value: A,
    inline transformationSite: "transformation" | "definition",
    inline configs: Field[A, B] | Case[A, B]*
  ): B = ${ createTransformationBetween[A, B]('value, 'transformationSite, 'configs) }

  private def createTransformationBetween[A: Type, B: Type](
    value: Expr[A],
    transformationSite: Expr["transformation" | "definition"],
    configs: Expr[Seq[Field[A, B] | Case[A, B]]]
  )(using Quotes): Expr[B] = {
    given TransformationSite = TransformationSite.fromStringExpr(transformationSite)
    val plan = Planner.between(Structure.of[A], Structure.of[B])
    val config = Configuration.parse(configs)
    createTransformation(value, plan, config).asExprOf[B]
  }

  inline def via[A, B, Func, Args <: FunctionArguments](
    value: A,
    function: Func,
    inline transformationSite: "transformation" | "definition",
    inline configs: Field[A, Args] | Case[A, Args]*
  ): B = ${ createTransformationVia[A, B, Func, Args]('value, 'function, 'transformationSite, 'configs) }

  transparent inline def viaInferred[A, Func, Args <: FunctionArguments](
    value: A,
    inline transformationSite: "transformation" | "definition",
    inline function: Func,
    inline configs: Field[A, Args] | Case[A, Args]*
  ): Any = ${ createTransformationViaInferred('value, 'function, 'transformationSite, 'configs) }

  private def createTransformationViaInferred[A: Type, Func: Type, Args <: FunctionArguments: Type](
    value: Expr[A],
    function: Expr[Func],
    transformationSite: Expr["transformation" | "definition"],
    configs: Expr[Seq[Field[A, Args] | Case[A, Args]]]
  )(using Quotes) = {
    given TransformationSite = TransformationSite.fromStringExpr(transformationSite)

    val plan =
      Function
        .fromExpr(function)
        .map(function => Planner.between(Structure.of[A], Structure.fromFunction(function)))
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

  private def createTransformationVia[A: Type, B: Type, Func: Type, Args <: FunctionArguments: Type](
    value: Expr[A],
    function: Expr[Func],
    transformationSite: Expr["transformation" | "definition"],
    configs: Expr[Seq[Field[A, Args] | Case[A, Args]]]
  )(using Quotes) = {
    given TransformationSite = TransformationSite.fromStringExpr(transformationSite)

    val plan =
      Function
        .fromFunctionArguments[Args, Func](function)
        .map(function => Planner.between(Structure.of[A], Structure.fromFunction(function)))
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
    createTransformation(value, plan, config).asExprOf[B]
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
            .filterNot(ogError => // filter out things that were successfully configured to not show these to the user
              ogError.message.target match
                case Target.Source =>
                  reconfiguredPlan.successes
                    .exists(succ => succ.target == Target.Source && succ.path.isAncestorOrSiblingOf(ogError.sourceContext))
                case Target.Dest =>
                  reconfiguredPlan.successes.exists(succ =>
                    succ.target == Target.Dest && succ.path.isAncestorOrSiblingOf(ogError.destContext)
                  )
            )

        val allErrors = errors ::: reconfiguredPlan.errors ::: ogErrors
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
