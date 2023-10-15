package io.github.arainko.ducktape

import io.github.arainko.ducktape.internal.PlanInterpreter

extension [Source] (source: Source) {
  inline def to[Dest]: Dest = PlanInterpreter.transformBetween[Source, Dest](source)

  def into[Dest]: AppliedBuilder[Source, Dest] = AppliedBuilder[Source, Dest](source)

  transparent inline def via(inline function: Any): Any = PlanInterpreter.transformVia[Source, Nothing](source, function)

  transparent inline def intoVia(inline function: Any): Any = AppliedViaBuilder.create(source, function)
}
