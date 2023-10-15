package io.github.arainko.ducktape

import io.github.arainko.ducktape.internal.PlanInterpreter

final class AppliedViaBuilder[Source, Args <: FunctionArguments] private (value: Source, function: Any) {
  transparent inline def transform(inline config: Field[Source, Args] | Case[Source, Args]*): Any = 
    PlanInterpreter.transformVia[Source, Args](value, function, config*)
}

object AppliedViaBuilder {
  private def instance[A](source: A, function: Any): AppliedViaBuilder[A, Nothing] =
    AppliedViaBuilder[A, Nothing](source, function)

  transparent inline def create[A](source: A, inline function: Any): Any = {
    val inst = instance(source, function)
    internal.Function.fillInTypes(function, inst)
  }
}
