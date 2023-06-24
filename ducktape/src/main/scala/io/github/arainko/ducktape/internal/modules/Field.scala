package io.github.arainko.ducktape.internal.modules

import io.github.arainko.ducktape.Transformer
import io.github.arainko.ducktape.fallible.FallibleTransformer

import scala.quoted.*

private[ducktape] final class Field(val name: String, val tpe: Type[?], defaultValue: => Option[Expr[Any]]) {

  lazy val default: Option[Expr[Any]] = defaultValue

  def transformerTo(that: Field)(using Quotes): Expr[Transformer[?, ?]] = {
    import quotes.reflect.*

    (tpe -> that.tpe) match {
      case '[src] -> '[dest] =>
        Implicits.search(TypeRepr.of[Transformer[src, dest]]) match {
          case success: ImplicitSearchSuccess => success.tree.asExprOf[Transformer[src, dest]]
          case err: ImplicitSearchFailure     => Failure.emit(Failure.TransformerNotFound(this, that, err.explanation))
        }
    }
  }

  def fallibleTransformerTo[F[+x]](that: Field)(using quotes: Quotes, F: Type[F]): Expr[FallibleTransformer[F, ?, ?]] = {
    import quotes.reflect.*

    (tpe -> that.tpe) match {
      case '[src] -> '[dest] =>
        Implicits.search(TypeRepr.of[FallibleTransformer[F, src, dest]]) match {
          case success: ImplicitSearchSuccess => success.tree.asExprOf[FallibleTransformer[F, src, dest]]
          case err: ImplicitSearchFailure =>
            Failure.emit(Failure.FallibleTransformerNotFound(F, this, that, err.explanation))
        }
    }
  }

  override def toString: String = s"Field($name)"

  def <:<(that: Field)(using Quotes): Boolean = {
    import quotes.reflect.*
    TypeRepr.of(using tpe) <:< TypeRepr.of(using that.tpe)
  }

}

private[ducktape] object Field {
  final case class Unwrapped(underlying: Field, value: Expr[Any])

  final case class Wrapped[F[+x]](underlying: Field, value: Expr[F[Any]])
}
