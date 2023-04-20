package io.github.arainko.ducktape.internal.modules

import io.github.arainko.ducktape.Transformer

import scala.quoted.*

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

  override def toString: String = s"Field($name)"

  def <:<(that: Field)(using Quotes): Boolean = {
    import quotes.reflect.*
    TypeRepr.of(using tpe) <:< TypeRepr.of(using that.tpe)
  }
}
