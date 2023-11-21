package io.github.arainko.ducktape

import io.github.arainko.ducktape.internal.{Transformations, TransformationSite}

final class AppliedBuilder[Source, Dest](value: Source) {
  inline def transform(inline config: Field[Source, Dest] | Case[Source, Dest]*): Dest =
    Transformations.between[Source, Dest](value, TransformationSite.Transformation, config*)
}
