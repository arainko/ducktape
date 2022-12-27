package io.github.arainko.ducktape.internal.macros

import io.github.arainko.ducktape.*

import scala.deriving.*
import scala.quoted.*

private[ducktape] object DerivationMacros {
  inline def deriveProductTransformer[Source, Dest](using
    Source: Mirror.ProductOf[Source],
    Dest: Mirror.ProductOf[Dest]
  ): Transformer[Source, Dest] = ${ deriveProductTransformerMacro('Source, 'Dest) }

  def deriveProductTransformerMacro[Source: Type, Dest: Type](
    Source: Expr[Mirror.ProductOf[Source]],
    Dest: Expr[Mirror.ProductOf[Dest]]
  )(using Quotes): Expr[Transformer[Source, Dest]] =
    '{ source => ${ ProductTransformerMacrosV2.transform[Source, Dest]('source, Source, Dest) } }

  inline def deriveCoproductTransformer[Source, Dest](using
    Source: Mirror.SumOf[Source],
    Dest: Mirror.SumOf[Dest]
  ): Transformer[Source, Dest] = ${ deriveCoproductTransformerMacro[Source, Dest]('Source, 'Dest) }

  def deriveCoproductTransformerMacro[Source: Type, Dest: Type](
    Source: Expr[Mirror.SumOf[Source]],
    Dest: Expr[Mirror.SumOf[Dest]]
  )(using Quotes): Expr[Transformer[Source, Dest]] =
    '{ source => ${ CoproductTransformerMacrosV2.transform[Source, Dest]('source, Source, Dest) } }

  inline def deriveToAnyValTransformer[Source, Dest <: AnyVal]: Transformer[Source, Dest] =
    ${ deriveToAnyValTransformerMacro[Source, Dest] }

  def deriveToAnyValTransformerMacro[Source: Type, Dest <: AnyVal: Type](using Quotes): Expr[Transformer[Source, Dest]] =
    '{ source => ${ ProductTransformerMacrosV2.transformToAnyVal('source) } }

  inline def deriveFromAnyValTransformer[Source <: AnyVal, Dest] =
    ${ deriveFromAnyValTransformerMacro[Source, Dest] }

  def deriveFromAnyValTransformerMacro[Source <: AnyVal: Type, Dest: Type](using Quotes): Expr[Transformer[Source, Dest]] =
    '{ source => ${ ProductTransformerMacrosV2.transformFromAnyVal('source) } }
}
