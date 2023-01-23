package io.github.arainko.ducktape.internal.modules

import scala.quoted.*

/**
 * Replaces all references to 'param' of the given TransformerLamba with `appliedTo`. Eg.:
 *
 * val appliedTo = "appliedTo"
 * val name = (((param: String) => new Name(param)): Transformer.ToAnyVal[String, Name]).transform(appliedTo)
 *
 * After replacing this piece of code will look like this:
 * val name = (((param: String) => new Name(appliedTo)): Transformer.ToAnyVal[String, Name]).transform(appliedTo)
 */
private[ducktape] object Replacer {
  def apply(using Quotes)(transformerLambda: TransformerLambda[quotes.type], appliedTo: Expr[Any])(
    toBeTransformed: quotes.reflect.Term
  ): quotes.reflect.Term = {
    import quotes.reflect.*
    val mapper =
      new TreeMap {
        override def transformTerm(tree: Term)(owner: Symbol): Term =
          tree match {
            // by checking symbols we know if the term refers to the TransformerLambda parameter so we can replace it
            case Select(ident: Ident, fieldName) if transformerLambda.param.symbol == ident.symbol =>
              Select.unique(appliedTo.asTerm, fieldName)
            case other => super.transformTerm(other)(owner)
          }
      }

    mapper.transformTerm(toBeTransformed)(Symbol.spliceOwner)
  }
}
