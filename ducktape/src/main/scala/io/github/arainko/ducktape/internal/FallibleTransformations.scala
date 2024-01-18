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

    TransformationMode
      .create(F)
      .map { mode =>
        val plan = Planner.between(sourceStruct, destStruct)
        plan.refine match
          case Left(value) =>
            quotes.reflect.report.errorAndAbort("woops")
          case Right(plan) =>
            FalliblePlanInterpreter.run[F, A, B](plan, source, mode).asExprOf[F[B]]
      }
      .getOrElse(quotes.reflect.report.errorAndAbort("woops"))


  }
}
