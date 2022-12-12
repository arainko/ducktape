package io.github.arainko.ducktape.internal.macros

import io.github.arainko.ducktape.internal.modules.*
import scala.quoted.*
import io.github.arainko.ducktape.Transformer

private[ducktape] final class LiftTransformationMacros(using val quotes: Quotes) extends Module, LiftTransformationModule

private[ducktape] object LiftTransformationMacros {
  inline def liftTransformation[Source, Dest](inline transformer: Transformer[Source, Dest], appliedTo: Source): Dest =
    ${ liftTransformationMacro('transformer, 'appliedTo) }

  def liftTransformationMacro[Source: Type, Dest: Type](
    transformer: Expr[Transformer[Source, Dest]],
    appliedTo: Expr[Source]
  )(using Quotes): Expr[Dest] =
    LiftTransformationMacros().liftTransformation(transformer, appliedTo)
}
