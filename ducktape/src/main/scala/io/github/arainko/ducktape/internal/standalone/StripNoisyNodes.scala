package io.github.arainko.ducktape.internal.standalone

import scala.quoted.*

object StripNoisyNodes {
  def apply[A: Type](expr: Expr[A])(using Quotes): Expr[A] = {
    import quotes.reflect.*

    val mapper =
      new TreeMap {
        override def transformTerm(tree: Term)(owner: Symbol): Term =
          tree match {
            case Inlined(_, Nil, term) => transformTerm(term)(owner)
            case other                 => super.transformTerm(other)(owner)
          }
      }

    mapper.transformTerm(expr.asTerm)(Symbol.spliceOwner).asExprOf[A]
  }
}
