package io.github.arainko.ducktape.internal.macros

import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.internal.modules.*

import scala.deriving.*
import scala.quoted.*

private[ducktape] object DerivedTransformers {
  inline def product[Source, Dest](using
    Source: Mirror.ProductOf[Source],
    Dest: Mirror.ProductOf[Dest]
  ): Transformer[Source, Dest] = ${ deriveProductTransformerMacro('Source, 'Dest) }

  def deriveProductTransformerMacro[Source: Type, Dest: Type](
    Source: Expr[Mirror.ProductOf[Source]],
    Dest: Expr[Mirror.ProductOf[Dest]]
  )(using Quotes): Expr[Transformer[Source, Dest]] =
    '{ source => ${ ProductTransformations.transform[Source, Dest]('source, Source, Dest) } }

  inline def coproduct[Source, Dest](using
    Source: Mirror.SumOf[Source],
    Dest: Mirror.SumOf[Dest]
  ): Transformer[Source, Dest] = ${ deriveCoproductTransformerMacro[Source, Dest]('Source, 'Dest) }

  def deriveCoproductTransformerMacro[Source: Type, Dest: Type](
    Source: Expr[Mirror.SumOf[Source]],
    Dest: Expr[Mirror.SumOf[Dest]]
  )(using Quotes): Expr[Transformer[Source, Dest]] =
    '{ source => ${ CoproductTransformations.transform[Source, Dest]('source, Source, Dest) } }

  inline def toAnyVal[Source, Dest <: AnyVal]: Transformer[Source, Dest] =
    ${ deriveToAnyValTransformerMacro[Source, Dest] }

  def deriveToAnyValTransformerMacro[Source: Type, Dest <: AnyVal: Type](using Quotes): Expr[Transformer[Source, Dest]] =
    '{ source => ${ ProductTransformations.transformToAnyVal('source) } }

  inline def fromAnyVal[Source <: AnyVal, Dest] =
    ${ deriveFromAnyValTransformerMacro[Source, Dest] }

  def deriveFromAnyValTransformerMacro[Source <: AnyVal: Type, Dest: Type](using Quotes): Expr[Transformer[Source, Dest]] =
    '{ source => ${ ProductTransformations.transformFromAnyVal('source) } }
}
