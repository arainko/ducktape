package io.github.arainko.ducktape

import io.github.arainko.ducktape.internal.Transformations

final class AppliedViaBuilder[Source, Func, Args <: FunctionArguments] private (value: Source, function: Func) {
  transparent inline def transform(inline config: Field[Source, Args] | Case[Source, Args]*): Any = 
    Transformations.via[Source, Func, Args](value, function, config*)
}

object AppliedViaBuilder {
  private def instance[A, Func](source: A, function: Func): AppliedViaBuilder[A, Func, Nothing] =
    AppliedViaBuilder[A, Func, Nothing](source, function)

  transparent inline def create[A, Func](source: A, inline function: Func): Any = {
    val inst = instance(source, function)
    internal.Function.encodeAsType(function, inst)
  }
}
