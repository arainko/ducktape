package io.github.arainko.ducktape

import io.github.arainko.ducktape.internal.macros.*
import io.github.arainko.ducktape.function.*
import scala.deriving.Mirror
import io.github.arainko.ducktape.builder.*

extension [Source](value: Source) {
  def into[Dest]: AppliedBuilder[Source, Dest] = AppliedBuilder(value)

  def to[Dest](using Transformer[Source, Dest]): Dest = Transformer[Source, Dest].transform(value)

  transparent inline def intoVia[Func](inline function: Func)(using Mirror.ProductOf[Source], FunctionMirror[Func]) =
    AppliedViaBuilder.create(value, function)

  inline def via[Func](inline function: Func)(using
    Func: FunctionMirror[Func],
    Source: Mirror.ProductOf[Source]
  ): Func.Return = ProductTransformerMacros.via(value, function)
}
