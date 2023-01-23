package io.github.arainko.ducktape.internal.modules

import scala.quoted.*

private[ducktape] object MakeTransformer {

  def unapply(using Quotes)(term: quotes.reflect.Term): Option[(quotes.reflect.ValDef, quotes.reflect.Term)] = {
    import quotes.reflect.*

    term match {
      case Inlined(_, Nil, term) =>
        unapply(term)
      case Untyped(Apply(_, List(Uninlined(Untyped(Uninlined(Lambda(List(param), Uninlined(body)))))))) =>
        Some(param -> body)
      case other =>
        None
    }
  }
}
