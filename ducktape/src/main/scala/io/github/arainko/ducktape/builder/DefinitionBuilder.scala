package io.github.arainko.ducktape.builder

import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.internal.macros.*

import scala.compiletime.*
import scala.deriving.Mirror

opaque type DefinitionBuilder[Source, Dest] = Unit

object DefinitionBuilder {
  def apply[Source, Dest]: DefinitionBuilder[Source, Dest] = ()

  extension [Source, Dest](builder: DefinitionBuilder[Source, Dest]) {

    //TODO: Extract this to a macro, mirrors don't need to be used at runtime here
    inline def define(
      inline config: FieldConfig[Source, Dest]*
    )(using Source: Mirror.Of[Source], Dest: Mirror.Of[Dest]): Transformer[Source, Dest] =
      new {
        def transform(from: Source): Dest =
          inline erasedValue[(Source.type, Dest.type)] match {
            case (_: Mirror.ProductOf[Source], _: Mirror.ProductOf[Dest]) =>
              ProductTransformerMacros.transformConfigured(from, config*)(using summonInline, summonInline)
            case (_: Mirror.SumOf[Source], _: Mirror.SumOf[Dest]) =>
              CoproductTransformerMacros.transformConfigured(from, config*)(using summonInline, summonInline)
          }
      }
  }

}
