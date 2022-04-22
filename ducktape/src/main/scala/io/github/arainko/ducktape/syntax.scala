package io.github.arainko.ducktape

import io.github.arainko.ducktape.Builder.Applied
import io.github.arainko.ducktape.internal.macros.*
import io.github.arainko.ducktape.function.*
import scala.deriving.Mirror

extension [From](value: From) {
  def into[To]: Applied[From, To, EmptyTuple] = Builder.applied[From, To](value)

  def to[To](using Transformer[From, To]): To = Transformer[From, To].transform(value)

  inline def via[Func](inline function: Func)(using
    Func: FunctionMirror[Func],
    A: Mirror.ProductOf[From]
  ): Func.Return = ProductTransformerMacros.via(value, function)
}
