package io.github.arainko.ducktape.builder

import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.internal.macros.*

import scala.compiletime.*
import scala.deriving.Mirror

final case class AppliedBuilder[Source, Dest](appliedTo: Source) {

  inline def apply(
    inline config: FieldConfig[Source, Dest]*
  )(using Source: Mirror.Of[Source], Dest: Mirror.Of[Dest]): Dest =
    inline erasedValue[(Source.type, Dest.type)] match {
      case (_: Mirror.ProductOf[Source], _: Mirror.ProductOf[Dest]) =>
        ProductTransformerMacros.transformConfigured(appliedTo, config*)(using summonInline, summonInline)
      case (_: Mirror.SumOf[Source], _: Mirror.SumOf[Dest]) =>
        CoproductTransformerMacros.transformConfigured(appliedTo, config*)(using summonInline, summonInline)
    }

}
