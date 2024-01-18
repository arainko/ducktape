package io.github.arainko.ducktape

import io.github.arainko.ducktape.internal.TotalTransformations

final class DefinitionViaBuilder[Source, Dest, Func, Args <: FunctionArguments] private (function: Func) {
  transparent inline def build(inline config: Field[Source, Args] | Case[Source, Args]*): Transformer[Source, Dest] =
    new Transformer[Source, Dest] {
      def transform(value: Source): Dest =
        TotalTransformations.via[Source, Dest, Func, Args](value, function, "definition", config*)
    }
}

object DefinitionViaBuilder {
  private def instance[A, Func](function: Func): DefinitionViaBuilder[A, Nothing, Func, Nothing] =
    DefinitionViaBuilder[A, Nothing, Func, Nothing](function)

  def create[Source]: PartiallyApplied[Source] = ()

  opaque type PartiallyApplied[Source] = Unit

  object PartiallyApplied {
    extension [Source](partial: PartiallyApplied[Source]) {
      transparent inline def apply[Func](inline function: Func): Any = {
        val builder = instance[Source, Func](function)
        internal.Function.encodeAsType[[args <: FunctionArguments, retTpe] =>> DefinitionViaBuilder[Source, retTpe, Func, args]](
          function,
          builder
        )
      }
    }
  }
}
