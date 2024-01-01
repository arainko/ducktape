package io.github.arainko.ducktape.internal

import scala.quoted.*
import io.github.arainko.ducktape.Mode

object FalliblePlanInterpreter {
  def run[F[+x], A](plan: Plan[Nothing], sourceValue: Expr[A], mode: Expr[Mode.Accumulating[F]])(using Quotes): Expr[F[Any]] = ???
}
