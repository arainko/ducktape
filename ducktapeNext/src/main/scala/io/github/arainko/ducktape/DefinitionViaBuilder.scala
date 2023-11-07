package io.github.arainko.ducktape

import io.github.arainko.ducktape.internal.Transformations

final class DefinitionViaBuilder[Source, Func, Args <: FunctionArguments] private (function: Func) {
  transparent inline def build(inline config: Field[Source, Args] | Case[Source, Args]*): Any =
    new Transformer[Source, Any] {
      def transform(value: Source): Any = Transformations.via[Source, Func, Args](value, function, config*)
    }
}

object DefinitionViaBuilder {
  private def instance[A, Func](function: Func): DefinitionViaBuilder[A, Func, Nothing] =
    DefinitionViaBuilder[A, Func, Nothing](function)

  def create[Source]: PartiallyApplied[Source] = ()

  opaque type PartiallyApplied[Source] = Unit

  object PartiallyApplied {
    extension [Source](partial: PartiallyApplied[Source]) {
      transparent inline def apply[Func](inline function: Func): Any = {
        val builder = instance[Source, Func](function)
        internal.Function.encodeAsType(function, builder)
      }
    }
  }
}
