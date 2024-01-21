package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.internal.Function.fromFunctionArguments

import scala.quoted.*

private[ducktape] object TotalTransformations {
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
    given Summoner[Nothing] = Summoner.Total

    val plan = Planner.between(Structure.of[A](Path.empty(Type.of[A])), Structure.of[B](Path.empty(Type.of[B])))
    val config = Configuration.parseTotal(configs, NonEmptyList(ConfigParser.Total))
    val totalPlan = Refiner.refineOrReportErrorsAndAbort(plan, config)
    PlanInterpreter.run[A](totalPlan, value).asExprOf[B]
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
    given Summoner[Nothing] = Summoner.Total

    val sourceStruct = Structure.of[A](Path.empty(Type.of[A]))

    val plan =
      Function
        .fromExpr(function)
        .map(function => Planner.between(sourceStruct, Structure.fromFunction(function)))
        .getOrElse(
          Plan.Error(
            sourceStruct,
            Structure.of[Any](Path.empty(Type.of[Any])),
            ErrorMessage.CouldntCreateTransformationFromFunction(Span.fromExpr(function)),
            None
          )
        )

    val config = Configuration.parseTotal(configs, NonEmptyList(ConfigParser.Total))
    val totalPlan = Refiner.refineOrReportErrorsAndAbort(plan, config)
    PlanInterpreter.run[A](totalPlan, value)
  }

  private def createTransformationVia[A: Type, B: Type, Func: Type, Args <: FunctionArguments: Type](
    value: Expr[A],
    function: Expr[Func],
    transformationSite: Expr["transformation" | "definition"],
    configs: Expr[Seq[Field[A, Args] | Case[A, Args]]]
  )(using Quotes) = {
    given TransformationSite = TransformationSite.fromStringExpr(transformationSite)
    given Summoner[Nothing] = Summoner.Total

    val sourceStruct = Structure.of[A](Path.empty(Type.of[A]))

    val plan =
      Function
        .fromFunctionArguments[Args, Func](function)
        .map(function => Planner.between(sourceStruct, Structure.fromFunction(function)))
        .getOrElse(
          Plan.Error(
            sourceStruct,
            Structure.of[Any](Path.empty(Type.of[Any])),
            ErrorMessage.CouldntCreateTransformationFromFunction(Span.fromExpr(function)),
            None
          )
        )

    val config = Configuration.parseTotal(configs, NonEmptyList(ConfigParser.Total))
    val totalPlan = Refiner.refineOrReportErrorsAndAbort(plan, config)
    PlanInterpreter.run[A](totalPlan, value).asExprOf[B]
  }
}