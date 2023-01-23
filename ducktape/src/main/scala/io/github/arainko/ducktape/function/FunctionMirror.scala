package io.github.arainko.ducktape.function

import io.github.arainko.ducktape.internal.macros.*

import scala.annotation.implicitNotFound

@implicitNotFound("FunctionMirrors are only available for function types, but got ${F}")
sealed trait FunctionMirror[F] {
  type Return
}

object FunctionMirror extends FunctionMirror[Any => Any] {
  type Aux[F, R] = FunctionMirror[F] {
    type Return = R
  }

  override type Return = Any

  transparent inline given [F]: FunctionMirror[F] = Functions.deriveMirror[F]
}
