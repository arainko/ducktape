package io.github.arainko.ducktape

import io.github.arainko.ducktape.internal.TotalTransformations

extension [Source](source: Source) {
  inline def to[Dest]: Dest = TotalTransformations.between[Source, Dest](source, "transformation")

  def into[Dest]: AppliedBuilder[Source, Dest] = AppliedBuilder[Source, Dest](source)

  transparent inline def via[Func](inline function: Func): Any =
    TotalTransformations.viaInferred[Source, Func, Nothing](source, "transformation", function)

  transparent inline def intoVia[Func](inline function: Func): Any =
    AppliedViaBuilder.create(source, function)
}
