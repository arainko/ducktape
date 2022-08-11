package io.github.arainko.ducktape.builder

import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.internal.macros.*

import scala.compiletime.*
import scala.deriving.Mirror

final class AppliedBuilder[Source, Dest](appliedTo: Source) {

  inline def apply(inline config: BuilderConfig[Source, Dest]*): Dest =
    TransformerMacros.transformConfigured(appliedTo, config)
}
