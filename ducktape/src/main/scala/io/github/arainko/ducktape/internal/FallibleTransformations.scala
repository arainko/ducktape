package io.github.arainko.ducktape.internal

import scala.quoted.*
import io.github.arainko.ducktape.*

object FallibleTransformations {
  inline def between[F[+x], A, B](
    source: A,
    F: Mode[F],
    inline transformationSite: "transformation" | "definition",
    inline configs: Field.Fallible[F, A, B] | Case.Fallible[F, A, B]*
  ) = ${ createTransformationBetween[F, A, B]('source, 'F, 'transformationSite, 'configs) }

  private def createTransformationBetween[F[+x]: Type, A: Type, B: Type](
    source: Expr[A],
    F: Expr[Mode[F]],
    transformationSite: Expr["transformation" | "definition"],
    configs: Expr[Seq[Field.Fallible[F, A, B] | Case.Fallible[F, A, B]]]
  )(using Quotes): Expr[F[B]] = {
    given Summoner[Fallible] = Summoner.PossiblyFallible[F]
    given TransformationSite = TransformationSite.fromStringExpr(transformationSite)

    val sourceStruct = Structure.of[A](Path.empty(Type.of[A]))
    val destStruct = Structure.of[B](Path.empty(Type.of[B]))
    val plan = Planner.between(sourceStruct, destStruct)
    val config = Configuration.parse(configs, NonEmptyList(ConfigParser.Total, ConfigParser.PossiblyFallible[F]))

    TransformationMode
      .create(F)
      .toRight(Plan.Error(sourceStruct, destStruct, ErrorMessage.UndeterminedTransformationMode(Span.fromExpr(F)), None))
      .match {
        case Left(error) =>
          Backend.reportErrorsAndAbort(NonEmptyList(error), Nil)
        case Right(mode) =>
          val totalPlan = Backend.refineOrReportErrorsAndAbort(plan, config)
          FalliblePlanInterpreter.run[F, A, B](totalPlan, source, mode).asExprOf[F[B]]
      }
  }

  inline def via[F[+x], A, B, Func, Args <: FunctionArguments](
    value: A,
    function: Func,
    F: Mode[F],
    inline transformationSite: "transformation" | "definition",
    inline configs: Field.Fallible[F, A, Args] | Case.Fallible[F, A, Args]*
  ): F[B] = ${ createTransformationVia[F, A, B, Func, Args]('value, 'function, 'F, 'transformationSite, 'configs) }

  private def createTransformationVia[F[+x]: Type, A: Type, B: Type, Func: Type, Args <: FunctionArguments: Type](
    value: Expr[A],
    function: Expr[Func],
    F: Expr[Mode[F]],
    transformationSite: Expr["transformation" | "definition"],
    configs: Expr[Seq[Field.Fallible[F, A, Args] | Case.Fallible[F, A, Args]]]
  )(using Quotes): Expr[F[B]] = {
    given Summoner[Fallible] = Summoner.PossiblyFallible[F]
    given TransformationSite = TransformationSite.fromStringExpr(transformationSite)

    val sourceStruct = Structure.of[A](Path.empty(Type.of[A]))
    val config = Configuration.parse(configs, NonEmptyList(ConfigParser.Total, ConfigParser.PossiblyFallible[F]))

    Function
      .fromFunctionArguments[Args, Func](function)
      .toRight(
        Plan.Error(
          sourceStruct,
          Structure.toplevelAny,
          ErrorMessage.CouldntCreateTransformationFromFunction(Span.fromExpr(function)),
          None
        )
      )
      .flatMap { func =>
        val destStruct = Structure.fromFunction(func)
        TransformationMode
          .create(F)
          .toRight(Plan.Error(sourceStruct, destStruct, ErrorMessage.UndeterminedTransformationMode(Span.fromExpr(F)), None))
          .map(mode => Planner.between(sourceStruct, destStruct) -> mode)
      }
      .match {
        case Left(error) => Backend.reportErrorsAndAbort(NonEmptyList(error), config)
        case Right(plan, mode) =>
          val totalPlan = Backend.refineOrReportErrorsAndAbort(plan, config)
          FalliblePlanInterpreter.run[F, A, B](totalPlan, value, mode).asExprOf[F[B]]
      }
  }

  transparent inline def viaInferred[F[+x], A, Func](value: A, inline function: Func, F: Mode[F]): F[Any] =
    ${ createTransformationViaInferred[F, A, Func]('value, 'function, 'F) }

  private def createTransformationViaInferred[F[+x]: Type, A: Type, Func](
    value: Expr[A],
    function: Expr[Func],
    F: Expr[Mode[F]]
  )(using Quotes): Expr[F[Any]] = {
    given Summoner[Fallible] = Summoner.PossiblyFallible[F]
    given TransformationSite = TransformationSite.Transformation

    val sourceStruct = Structure.of[A](Path.empty(Type.of[A]))

    Function
      .fromExpr(function)
      .toRight(
        Plan.Error(
          sourceStruct,
          Structure.toplevelAny,
          ErrorMessage.CouldntCreateTransformationFromFunction(Span.fromExpr(function)),
          None
        )
      )
      .flatMap { func =>
        val destStruct = Structure.fromFunction(func)
        TransformationMode
          .create(F)
          .toRight(Plan.Error(sourceStruct, destStruct, ErrorMessage.UndeterminedTransformationMode(Span.fromExpr(F)), None))
          .map(mode => Planner.between(sourceStruct, destStruct) -> mode)
      }
      .match {
        case Left(error) => Backend.reportErrorsAndAbort(NonEmptyList(error), Nil)
        case Right(plan, mode) =>
          plan.dest.tpe match {
            case '[dest] =>
              val totalPlan = Backend.refineOrReportErrorsAndAbort(plan, Nil)
              FalliblePlanInterpreter.run[F, A, dest](totalPlan, value, mode).asExprOf[F[dest]]
          }
      }
  }
}
