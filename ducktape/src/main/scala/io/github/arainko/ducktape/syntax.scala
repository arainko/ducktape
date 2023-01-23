package io.github.arainko.ducktape

import io.github.arainko.ducktape.builder.*
import io.github.arainko.ducktape.function.*
import io.github.arainko.ducktape.internal.macros.*
import io.github.arainko.ducktape.internal.modules.*

import scala.deriving.Mirror

extension [Source](value: Source) {
  def into[Dest]: AppliedBuilder[Source, Dest] = AppliedBuilder(value)

  // TODO: Introduce in ducktape 0.2 as a replacement for `.to`, this will break binary compat
  // inline def transformInto[Dest](using inline transformer: Transformer[Source, Dest]) =
  //   ${ LiftTransformation.liftTransformation('transformer, 'value) }

  def to[Dest](using Transformer[Source, Dest]): Dest = Transformer[Source, Dest].transform(value)

  transparent inline def intoVia[Func](inline function: Func)(using Mirror.ProductOf[Source], FunctionMirror[Func]) =
    AppliedViaBuilder.create(value, function)

  inline def via[Func](inline function: Func)(using
    Func: FunctionMirror[Func],
    Source: Mirror.ProductOf[Source]
  ): Func.Return = Transformations.via(value, function)
}
