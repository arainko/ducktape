package io.github.arainko.ducktape

import io.github.arainko.ducktape.internal.TotalTransformations

final class AppliedViaBuilder[Source, Dest, Func, Args <: FunctionArguments] private (value: Source, function: Func) {
  inline def transform(inline config: Field[Source, Args] | Case[Source, Args]*): Dest =
    TotalTransformations.via[Source, Dest, Func, Args](value, function, "transformation", config*)
}

object AppliedViaBuilder {
  private def instance[A, Func](source: A, function: Func): AppliedViaBuilder[A, Nothing, Func, Nothing] =
    AppliedViaBuilder[A, Nothing, Func, Nothing](source, function)

  transparent inline def create[A, Func](source: A, inline function: Func): Any = {
    val inst = instance(source, function)
    internal.Function
      .encodeAsType[[args <: FunctionArguments, retTpe] =>> AppliedViaBuilder[A, retTpe, Func, args]](function, inst)
  }
}
