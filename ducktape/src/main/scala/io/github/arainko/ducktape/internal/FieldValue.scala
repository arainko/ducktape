package io.github.arainko.ducktape.internal

import scala.quoted.*

private[ducktape] enum FieldValue {
  def index: Int
  def tpe: Type[?]

  case Wrapped[F[+x]](index: Int, tpe: Type[?], value: Expr[F[Any]])
  case Unwrapped(index: Int, tpe: Type[?], value: Expr[Any])
}

private[ducktape] object FieldValue {

  given Ordering[FieldValue] = Ordering.by(_.index)

  extension [F[+x]](wrapped: FieldValue.Wrapped[F]) {
    def unwrapped(value: Expr[Any]): FieldValue.Unwrapped = FieldValue.Unwrapped(wrapped.index, wrapped.tpe, value)
  }
}
