package io.github.arainko.ducktape.internal.modules

import scala.quoted.*

private[ducktape] object FunctionLambda {
  def unapply(using Quotes)(arg: quotes.reflect.Term): Option[(List[quotes.reflect.ValDef], quotes.reflect.Term)] = {
    import quotes.reflect.*

    arg match {
      case Inlined(_, _, Lambda(vals, term)) => Some(vals -> term)
      case Inlined(_, _, nested)             => FunctionLambda.unapply(nested)
      case _                                 => None
    }
  }
}
