package io.github.arainko.ducktape.builder

import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.internal.macros.*

import scala.compiletime.*
import scala.deriving.Mirror
import java.awt.Color

final class DefinitionBuilder[Source, Dest] {
  inline def apply(inline config: BuilderConfig[Source, Dest]*): Transformer[Source, Dest] =
    new {
      def transform(from: Source): Dest =
        ProductTransformerMacros.transformWhateverConfigured(from, config*)
    }
}