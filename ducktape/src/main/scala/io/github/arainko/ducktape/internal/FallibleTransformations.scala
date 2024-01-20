package io.github.arainko.ducktape.internal

import scala.quoted.*
import io.github.arainko.ducktape.*

object FallibleTransformations {
  inline def between[F[+x], A, B](
    source: A,
    F: Mode[F],
    inline configs: Field.Fallible[F, A, B] | Case.Fallible[F, A, B]*
    ) = ${ createTransformationBetween[F, A, B]('source, 'F, 'configs) }

  def createTransformationBetween[F[+x]: Type, A: Type, B: Type](
    source: Expr[A],
    F: Expr[Mode[F]],
    configs: Expr[Seq[Field.Fallible[F, A, B] | Case.Fallible[F, A, B]]],
  )(using Quotes): Expr[F[B]] = {
    given Summoner[Fallible] = Summoner.PossiblyFallible[F]
    given TransformationSite = TransformationSite.Transformation

    val sourceStruct = Structure.of[A](Path.empty(Type.of[A]))
    val destStruct = Structure.of[B](Path.empty(Type.of[B]))
    val plan = Planner.between(sourceStruct, destStruct)
    val config = Configuration.parse(configs, NonEmptyList(ConfigParser.Total, ConfigParser.PossiblyFallible[F]))

    TransformationMode
      .create(F)
      .toRight(Plan.Error(sourceStruct, destStruct, ErrorMessage.UndeterminedTransformationMode(Span.fromExpr(F)), None))
      .match {
        case Left(error) =>
          Refiner.reportErrorsAndAbort(NonEmptyList(error), Nil)
        case Right(mode) =>
          val totalPlan = Refiner.refineOrReportErrorsAndAbort(plan, config)
          FalliblePlanInterpreter.run[F, A, B](totalPlan, source, mode).asExprOf[F[B]]
      }
  }
}
