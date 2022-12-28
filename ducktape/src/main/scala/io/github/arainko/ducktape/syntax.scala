package io.github.arainko.ducktape

import io.github.arainko.ducktape.builder.*
import io.github.arainko.ducktape.function.*
import io.github.arainko.ducktape.internal.macros.*
import io.github.arainko.ducktape.internal.modules.*

import scala.deriving.Mirror

extension [Source](value: Source) {
  def into[Dest]: AppliedBuilder[Source, Dest] = AppliedBuilder(value)

  inline def transformInto[Dest](using inline transformer: Transformer[Source, Dest]) =
    Transformations.liftFromTransformer(value)

  @deprecated(message = "Use '.transformInto' instead, it includes some additional optimizations", since = "0.1.2")
  def to[Dest](using Transformer[Source, Dest]): Dest = Transformer[Source, Dest].transform(value)

  transparent inline def intoVia[Func](inline function: Func)(using Mirror.ProductOf[Source], FunctionMirror[Func]): Any =
    AppliedViaBuilder.create(value, function)

  inline def via[Func](inline function: Func)(using
    Func: FunctionMirror[Func],
    Source: Mirror.ProductOf[Source]
  ): Func.Return = Transformations.via(value, function)
}
