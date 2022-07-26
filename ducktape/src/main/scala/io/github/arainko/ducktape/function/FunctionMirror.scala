package io.github.arainko.ducktape.function

import io.github.arainko.ducktape.internal.macros.*
import scala.annotation.implicitNotFound

@implicitNotFound("FunctionMirrors are only available for function types, but got ${F}")
sealed trait FunctionMirror[F] {
  type Args <: Tuple
  type Return
}

object FunctionMirror extends FunctionMirror[Any => Any] {
  type Aux[F, A <: Tuple, R] = FunctionMirror[F] {
    type Args = A
    type Return = R
  }

  override type Args = Tuple1[Any]

  override type Return = Any

  transparent inline given [F]: FunctionMirror[F] = FunctionMacros.createMirror[F]
}
