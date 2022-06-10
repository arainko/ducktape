package io.github.arainko.ducktape

import io.github.arainko.ducktape.internal.macros.*
import io.github.arainko.ducktape.function.*
import scala.deriving.Mirror

extension [From](value: From) {
  // def into[To]: Applied[From, To, EmptyTuple] = Builder.applied[From, To](value)

  def to[To](using Transformer[From, To]): To = Transformer[From, To].transform(value)

  transparent inline def intoVia[Func](inline function: Func)(using From: Mirror.ProductOf[From], Func: FunctionMirror[Func]) =
    ViaBuilder.applied(value, function)

  inline def via[Func](inline function: Func)(using
    Func: FunctionMirror[Func],
    A: Mirror.ProductOf[From]
  ): Func.Return = ProductTransformerMacros.via(value, function)
}