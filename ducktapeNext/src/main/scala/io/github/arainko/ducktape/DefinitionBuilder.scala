package io.github.arainko.ducktape

import io.github.arainko.ducktape.internal.Transformations

class DefinitionBuilder[Source, Dest] {
  inline def build(inline config: Field[Source, Dest] | Case[Source, Dest]*): Transformer[Source, Dest] = source =>
    Transformations.between[Source, Dest](source, config*)
}
