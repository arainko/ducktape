package io.github.arainko.ducktape.internal

import scala.quoted.*

private[ducktape] enum FieldValue {
  def name: String
  def tpe: Type[?]

  case Wrapped[F[+x]](name: String, tpe: Type[?], value: Expr[F[Any]])
  case Unwrapped(name: String, tpe: Type[?], value: Expr[Any])
}

private[ducktape] object FieldValue {
  extension [F[+x]](wrapped: FieldValue.Wrapped[F]) {
    def unwrapped(value: Expr[Any]): FieldValue.Unwrapped = FieldValue.Unwrapped(wrapped.name, wrapped.tpe, value)
  }
}
