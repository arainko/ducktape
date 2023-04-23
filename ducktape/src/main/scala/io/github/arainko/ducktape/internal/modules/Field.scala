package io.github.arainko.ducktape.internal.modules

import io.github.arainko.ducktape.Transformer

import scala.quoted.*
import io.github.arainko.ducktape.fallible.FallibleTransformer

private[ducktape] final class Field(val name: String, val tpe: Type[?], val default: Option[Expr[Any]]) {
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

  // This untyped due to not being able to reduce a HKT with wildcards
  def partialTransformerTo[
    F[+x],
    PartialTransformer[f[+x], a, b] <: FallibleTransformer[f, a, b]
  ](that: Field)(using quotes: Quotes, F: Type[F], PartialTransformer: Type[PartialTransformer]): quotes.reflect.Term = {
    import quotes.reflect.*

    (tpe -> that.tpe) match {
      case '[src] -> '[dest] =>
        Implicits.search(TypeRepr.of[PartialTransformer[F, src, dest]]) match {
          case success: ImplicitSearchSuccess => success.tree
          case err: ImplicitSearchFailure =>
            Failure.emit(Failure.FallibleTransformerNotFound(PartialTransformer, this, that, err.explanation))
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
