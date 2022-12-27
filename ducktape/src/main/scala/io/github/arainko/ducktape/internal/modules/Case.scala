package io.github.arainko.ducktape.internal.modules

import scala.quoted.*

final case class Case(
  val name: String,
  val tpe: Type[?],
  val ordinal: Int
) {
  def materializeSingleton(using Quotes): Option[quotes.reflect.Term] = {
    import quotes.reflect.*

    val typeRepr = TypeRepr.of(using tpe)

    Option.when(typeRepr.isSingleton) {
      typeRepr match { case TermRef(a, b) => Ident(TermRef(a, b)) }
    }
  }
}
