package io.github.arainko.ducktape

import io.github.arainko.ducktape.internal.TotalTransformations

final class AppliedBuilder[Source, Dest](value: Source) {
  inline def transform(inline config: Field[Source, Dest] | Case[Source, Dest]*): Dest =
    TotalTransformations.between[Source, Dest](value, "transformation", config*)
}
