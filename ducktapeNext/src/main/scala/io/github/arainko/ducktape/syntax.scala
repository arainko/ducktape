package io.github.arainko.ducktape

import io.github.arainko.ducktape.internal.{Transformations, TransformationSite}

extension [Source](source: Source) {
  inline def to[Dest]: Dest = Transformations.between[Source, Dest](source, TransformationSite.Transformation)

  def into[Dest]: AppliedBuilder[Source, Dest] = AppliedBuilder[Source, Dest](source)

  transparent inline def via[Func](inline function: Func): Any =
    Transformations.viaInferred[Source, Func, Nothing](source, TransformationSite.Transformation, function)

  transparent inline def intoVia[Func](inline function: Func): Any =
    AppliedViaBuilder.create(source, function)
}
