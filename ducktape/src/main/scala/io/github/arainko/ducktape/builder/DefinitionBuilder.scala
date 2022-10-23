package io.github.arainko.ducktape.builder

import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.internal.macros.*

import scala.compiletime.*
import scala.deriving.Mirror

final class DefinitionBuilder[Source, Dest] {
  inline def build(inline config: BuilderConfig[Source, Dest]*): Transformer[Source, Dest] =
    from => /*NormalizationMacros.normalize(*/DebugMacros.code(TransformerMacros.transformConfigured(from, config))/*)*/
}
