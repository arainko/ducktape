package io.github.arainko.ducktape.internal.modules

import io.github.arainko.ducktape.Transformer

import scala.quoted.*

private[ducktape] final case class Case(
  val name: String,
  val tpe: Type[?]
) {

  def transformerTo(that: Case)(using Quotes): Either[String, Expr[Transformer[?, ?]]] = {
    import quotes.reflect.*

    (tpe -> that.tpe) match {
      case '[src] -> '[dest] =>
        Implicits.search(TypeRepr.of[Transformer[src, dest]]) match {
          case success: ImplicitSearchSuccess => Right(success.tree.asExprOf[Transformer[src, dest]])
          case err: ImplicitSearchFailure     => Left(err.explanation)
        }
    }
  }

  def materializeSingleton(using Quotes): Option[Expr[Any]] = {
    import quotes.reflect.*

    val typeRepr = TypeRepr.of(using tpe)

    Option.when(typeRepr.isSingleton) {
      typeRepr match { case ref: TermRef => Ident(ref).asExpr }
    }
  }
}
