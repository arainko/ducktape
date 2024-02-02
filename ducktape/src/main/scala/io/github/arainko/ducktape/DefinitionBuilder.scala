package io.github.arainko.ducktape

import io.github.arainko.ducktape.internal.TotalTransformations
import io.github.arainko.ducktape.internal.FallibleTransformations

final class DefinitionBuilder[Source, Dest] private[ducktape] {
  inline def build(inline config: Field[Source, Dest] | Case[Source, Dest]*): Transformer[Source, Dest] =
    Transformer.Derived.FromFunction(source => TotalTransformations.between[Source, Dest](source, "definition", config*))
}

object DefinitionBuilder {

  final class Fallible[F[+x], M <: Mode[F], Source, Dest] private[ducktape] (using F: M) {
    inline def build(
      inline config: Case.Fallible[F, Source, Dest] | Field.Fallible[F, Source, Dest]*
    ): Transformer.Fallible[F, Source, Dest] = 
      Transformer.Fallible.Derived.FromFunction(source => FallibleTransformations.between[F, Source, Dest](source, F, "definition", config*))
      
  }
}
