package io.github.arainko.ducktape.internal.standalone

import scala.quoted.*

object Untyped {
  def unapply(using Quotes)(term: quotes.reflect.Term): Some[quotes.reflect.Term] = {
    import quotes.reflect.*

    term match {
      case Typed(tree, _) => unapply(tree)
      case other          => Some(other)
    }
  }
}
