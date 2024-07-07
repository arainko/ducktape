package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.*

import scala.quoted.*

private[ducktape] object FallibleTransformations {
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
    given Context.PossiblyFallible[F](
      WrapperType.Wrapped(Type.of[F]),
      TransformationSite.fromStringExpr(transformationSite),
      Summoner.PossiblyFallible[F],
      TransformationMode.create(F)
    )

    val sourceStruct = Structure.of[A](Path.empty(Type.of[A]))
    val destStruct = Structure.of[B](Path.empty(Type.of[B]))
    val plan = Planner.between(sourceStruct, destStruct)
    val config = Configuration.parse(configs, ConfigParser.fallible[F])

    val totalPlan = Backend.refineOrReportErrorsAndAbort(plan, config)
    FalliblePlanInterpreter.run[F, A, B](totalPlan, source, Context.current.mode).asExprOf[F[B]]
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
    given Context.PossiblyFallible[F](
      WrapperType.Wrapped(Type.of[F]),
      TransformationSite.fromStringExpr(transformationSite),
      Summoner.PossiblyFallible[F],
      TransformationMode.create(F)
    )

    val sourceStruct = Structure.of[A](Path.empty(Type.of[A]))
    val config = Configuration.parse(configs, ConfigParser.fallible[F])

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
      .map { func =>
        val destStruct = Structure.fromFunction(func)
        Planner.between(sourceStruct, destStruct)
      }
      .match {
        case Left(error) => Backend.reportErrorsAndAbort(NonEmptyList(error), config)
        case Right(plan) =>
          val totalPlan = Backend.refineOrReportErrorsAndAbort(plan, config)
          FalliblePlanInterpreter.run[F, A, B](totalPlan, value, Context.current.mode).asExprOf[F[B]]
      }
  }

  transparent inline def viaInferred[F[+x], A, Func](value: A, inline function: Func, F: Mode[F]): F[Any] =
    ${ createTransformationViaInferred[F, A, Func]('value, 'function, 'F) }

  private def createTransformationViaInferred[F[+x]: Type, A: Type, Func](
    value: Expr[A],
    function: Expr[Func],
    F: Expr[Mode[F]]
  )(using Quotes): Expr[F[Any]] = {
    import quotes.reflect.*

    given Context.PossiblyFallible[F](
      WrapperType.Wrapped(Type.of[F]),
      TransformationSite.Transformation,
      Summoner.PossiblyFallible[F],
      TransformationMode.create(F)
    )

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
      .map { func =>
        val destStruct = Structure.fromFunction(func)
        Planner.between(sourceStruct, destStruct)
      }
      .match {
        case Left(error) => Backend.reportErrorsAndAbort(NonEmptyList(error), Nil)
        case Right(plan) =>
          plan.dest.tpe match {
            case '[dest] =>
              val totalPlan = Backend.refineOrReportErrorsAndAbort(plan, Nil)
              FalliblePlanInterpreter.run[F, A, dest](totalPlan, value, Context.current.mode).asExprOf[F[dest]]
          }
      }
  }
}
