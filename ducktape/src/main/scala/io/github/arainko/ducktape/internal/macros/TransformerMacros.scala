package io.github.arainko.ducktape.internal.macros

import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.internal.modules.*

import scala.deriving.*
import scala.quoted.*

private[ducktape] final class TransformerMacros(using val quotes: Quotes) extends Module {
  import quotes.reflect.*

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
      .getOrElse(
        report.errorAndAbort("Configured transformations are supported for Product -> Product and Coproduct -> Coproduct.")
      )

}

private[ducktape] object TransformerMacros {

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
