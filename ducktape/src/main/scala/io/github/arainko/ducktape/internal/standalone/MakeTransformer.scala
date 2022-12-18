package io.github.arainko.ducktape.internal.standalone

import scala.quoted.*

object MakeTransformer {
  def unapply(using Quotes)(term: quotes.reflect.Term): Option[(quotes.reflect.ValDef, quotes.reflect.Term)] = {
    import quotes.reflect.*

    PartialFunction.condOpt(term) {
      case Untyped(Apply(_, List(Untyped(Lambda(List(param), body))))) => param -> body
    }
  }
}
