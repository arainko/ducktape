package io.github.arainko.ducktape.internal.macros

import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.internal.modules.*

import scala.deriving.*
import scala.quoted.*

private[ducktape] final class TransformerMacros(using val quotes: Quotes) extends Module {
  import quotes.reflect.*

  def transform[Source: Type, Dest: Type](sourceValue: Expr[Source]) =
    mirrorOf[Source]
      .zip(mirrorOf[Dest])
      .collect {
        case '{ $source: Mirror.ProductOf[Source] } -> '{ $dest: Mirror.ProductOf[Dest] } =>
          ProductTransformerMacros.transformMacro(sourceValue, source, dest)
        case '{ $source: Mirror.SumOf[Source] } -> '{ $dest: Mirror.SumOf[Dest] } =>
          CoproductTransformerMacros.transformMacro(sourceValue, source, dest)
      }
      .getOrElse(report.errorAndAbort("BARF"))

  def transformConfigured[Source: Type, Dest: Type](
    sourceValue: Expr[Source],
    config: Expr[Seq[BuilderConfig[Source, Dest]]]
  ) =
    mirrorOf[Source]
      .zip(mirrorOf[Dest])
      .collect {
        case '{ $source: Mirror.ProductOf[Source] } -> '{ $dest: Mirror.ProductOf[Dest] } =>
          ProductTransformerMacros.transformConfiguredMacro(sourceValue, config, source, dest)
        case '{ $source: Mirror.SumOf[Source] } -> '{ $dest: Mirror.SumOf[Dest] } =>
          CoproductTransformerMacros.transformConfiguredMacro(sourceValue, config, source, dest)
      }
      .getOrElse(report.errorAndAbort("BARF"))

}

object TransformerMacros {
  inline def transform[Source, Dest](sourceValue: Source): Dest = ${ transformMacro('sourceValue) }

  def transformMacro[Source: Type, Dest](sourceValue: Expr[Source])(using Quotes): Expr[Dest] =
    TransformerMacros().transform(sourceValue)

  inline def transformConfigured[Source, Dest](
    sourceValue: Source,
    inline config: Seq[BuilderConfig[Source, Dest]]
  ): Dest = ${ transformConfiguredMacro('sourceValue, 'config) }

  def transformConfiguredMacro[Source: Type, Dest: Type](
    sourceValue: Expr[Source],
    config: Expr[Seq[BuilderConfig[Source, Dest]]]
  )(using Quotes) =
    TransformerMacros().transformConfigured(sourceValue, config)
}
