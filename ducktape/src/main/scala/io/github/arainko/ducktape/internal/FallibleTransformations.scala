package io.github.arainko.ducktape.internal

import scala.quoted.*
import io.github.arainko.ducktape.Mode

object FallibleTransformations {
  inline def between[F[+x], A, B](source: A, F: Mode.Accumulating[F]) = ${ createTransformationBetween[F, A, B]('source, 'F) }

  def createTransformationBetween[F[+x]: Type, A: Type, B: Type](
    source: Expr[A],
    F: Expr[Mode.Accumulating[F]]
  )(using Quotes): Expr[F[B]] = {
    given Summoner[Fallible] = Summoner.PossiblyFallible[F]
    given TransformationSite = TransformationSite.Transformation

    val plan = Planner.between(Structure.of[A](Path.empty(Type.of[A])), Structure.of[B](Path.empty(Type.of[B])))
    plan.refine match
      case Left(value) =>
        quotes.reflect.report.errorAndAbort("woops")
      case Right(plan) =>
        FalliblePlanInterpreter.run[F, A, B](plan, source, F).asExprOf[F[B]]

  }
}
