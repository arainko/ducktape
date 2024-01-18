package io.github.arainko.ducktape

import io.github.arainko.ducktape.internal.TotalTransformations

final class DefinitionBuilder[Source, Dest] {
  inline def build(inline config: Field[Source, Dest] | Case[Source, Dest]*): Transformer[Source, Dest] = source =>
    TotalTransformations.between[Source, Dest](source, "definition", config*)
}
