package io.github.arainko.ducktape.internal.modules

import scala.annotation.tailrec
import scala.quoted.*

private[ducktape] object MakeTransformer {

  /**
   * Tries to match on `Transformer.ForProduct.make(param => body)` (or any of the special cased Transformers that have a `make` method)
   * and extract `param` and `body` of the lambda along with definitions introduced in traversed `Inlined` nodes in case these are used in the AST.
   */
  def unapply(using Quotes)(
    term: quotes.reflect.Term
  ): Option[(quotes.reflect.ValDef, List[quotes.reflect.Definition], quotes.reflect.Term)] = {
    import quotes.reflect.*

    @tailrec
    def recurse(
      term: quotes.reflect.Term,
      defAcc: List[quotes.reflect.Definition]
    ): Option[(quotes.reflect.ValDef, List[quotes.reflect.Definition], quotes.reflect.Term)] =
      term match {
        case Inlined(_, defs, term) =>
          recurse(term, defAcc ::: defs)
        case Untyped(Apply(_, List(Uninlined(Untyped(Uninlined(Lambda(List(param), Uninlined(body)))))))) =>
          Some((param, defAcc, body))
        case other =>
          None
      }

    recurse(term, Nil)
  }
}
