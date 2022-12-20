package io.github.arainko.ducktape.internal.standalone

import scala.quoted.*
import io.github.arainko.ducktape.Transformer

object TransformerInvocation {
  def unapply(using Quotes)(term: quotes.reflect.Term): Option[(TransformerLambda[quotes.type], quotes.reflect.Term)] = {
    import quotes.reflect.*

    term.asExpr match {
      case '{ ($transformer: Transformer.ForProduct[a, b]).transform($appliedTo) } =>
        TransformerLambda.fromForProduct(transformer).map(_ -> appliedTo.asTerm)
      case '{ ($transformer: Transformer.FromAnyVal[a, b]).transform($appliedTo) } =>
        TransformerLambda.fromFromAnyVal(transformer).map(_ -> appliedTo.asTerm)
      case '{ ($transformer: Transformer.ToAnyVal[a, b]).transform($appliedTo) } =>
        TransformerLambda.fromToAnyVal(transformer).map(_ -> appliedTo.asTerm)
      case other => None
    }
  }
}
