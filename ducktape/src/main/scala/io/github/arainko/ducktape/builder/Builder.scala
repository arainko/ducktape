package io.github.arainko.ducktape

import io.github.arainko.ducktape.internal.macros.*
import scala.deriving.Mirror
import scala.collection.immutable.Set.Set4

opaque type Builder[Source, Dest] = Source

object Builder {
  inline def apply[Source, Dest](src: Source): Builder[Source, Dest] = src

  extension [Source, Dest](appliedTo: Builder[Source, Dest]) {
    inline def transform(
      inline config: FieldConfig[Source, Dest]*
    )(using Mirror.ProductOf[Source], Mirror.ProductOf[Dest]): Dest =
      ProductTransformerMacros.transformConfigured(appliedTo, config*)

    inline def define(
      inline config: FieldConfig[Source, Dest]*
    )(using Mirror.ProductOf[Source], Mirror.ProductOf[Dest]): Transformer[Source, Dest] =
      new {
        def transform(from: Source): Dest = ProductTransformerMacros.transformConfigured(appliedTo, config*)
      }

  }
}
