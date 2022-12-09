package io.github.arainko.ducktape.builder

import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.internal.macros.*

final class AppliedBuilder[Source, Dest](appliedTo: Source) {

  inline def transform(inline config: BuilderConfig[Source, Dest]*): Dest = 
    TransformerMacros.transformConfigured(appliedTo, config)
    
}
