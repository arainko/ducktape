package io.github.arainko.ducktape

import io.github.arainko.ducktape.builder.*
import io.github.arainko.ducktape.function.*
import io.github.arainko.ducktape.internal.macros.*

import scala.deriving.Mirror
import scala.annotation.targetName

extension [Source](value: Source) {
  def into[Dest]: AppliedBuilder[Source, Dest] = AppliedBuilder(value)

  inline def transformInto[Dest](using inline transformer: Transformer[Source, Dest]): Dest = 
    LiftTransformationMacros.liftTransformation(transformer, value)

  @deprecated(message = "Use 'transformInto' instead", since = "0.1.0")
  def to[Dest](using Transformer[Source, Dest]): Dest = Transformer[Source, Dest].transform(value)
  
  transparent inline def intoVia[Func](inline function: Func)(using Mirror.ProductOf[Source], FunctionMirror[Func]) =
    AppliedViaBuilder.create(value, function)

  inline def via[Func](inline function: Func)(using
    Func: FunctionMirror[Func],
    Source: Mirror.ProductOf[Source]
  ): Func.Return = ProductTransformerMacros.via(value, function)
}
