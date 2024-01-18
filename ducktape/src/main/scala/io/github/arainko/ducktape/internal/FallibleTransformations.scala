package io.github.arainko.ducktape.internal

import scala.quoted.*
import io.github.arainko.ducktape.Mode

object FallibleTransformations {
  inline def between[F[+x], A, B](source: A, F: Mode[F]) = ${ createTransformationBetween[F, A, B]('source, 'F) }

  def createTransformationBetween[F[+x]: Type, A: Type, B: Type](
    source: Expr[A],
    F: Expr[Mode[F]]
  )(using Quotes): Expr[F[B]] = {
    given Summoner[Fallible] = Summoner.PossiblyFallible[F]
    given TransformationSite = TransformationSite.Transformation

    val sourceStruct = Structure.of[A](Path.empty(Type.of[A]))
    val destStruct = Structure.of[B](Path.empty(Type.of[B]))
    val plan = Planner.between(sourceStruct, destStruct)

    TransformationMode
      .create(F)
      .toRight(Plan.Error(sourceStruct, destStruct, ErrorMessage.UndeterminedTransformationMode(Span.fromExpr(F)), None))
      .match {
        case Left(error) => 
          Transformations.createOrReportErrors(error, Nil)(_ => ???) //TODO: Fix this
        case Right(mode) =>
          Transformations.createOrReportErrors(plan, Nil)(totalPlan => FalliblePlanInterpreter.run[F, A, B](totalPlan, source, mode))
      }
      .asExprOf[F[B]]
  }
}
