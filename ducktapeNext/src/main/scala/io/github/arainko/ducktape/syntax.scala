package io.github.arainko.ducktape

import io.github.arainko.ducktape.internal.Transformations

extension [Source] (source: Source) {
  inline def to[Dest]: Dest = Transformations.between[Source, Dest](source)

  def into[Dest]: AppliedBuilder[Source, Dest] = AppliedBuilder[Source, Dest](source)

  transparent inline def via[Func](inline function: Func): Any = 
    Transformations.via[Source, Func, Nothing](source, function)

  transparent inline def intoVia[Func](inline function: Func): Any = 
    AppliedViaBuilder.create(source, function)
}
