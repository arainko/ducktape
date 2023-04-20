package io.github.arainko.ducktape.fallible.model

import io.github.arainko.ducktape.Transformer

def AlwaysValid[A]: A => Either[String, Unit] = _ => Right(())

def MaxSize(value: Int): String => Either[String, Unit] = 
  str => Either.cond(str.size <= value, (), "")

abstract class NewtypeValidated[A](f: A => Either[String, Unit]) {
  opaque type Type = A

  def make(value: A): Either[String, Type] = f(value).map(_ => value)

  def unsafe(value: A): Type = value

  given failFastTransformer: Transformer.FailFast[[A] =>> Either[String, A], A, Type] = make(_)

  given accTransformer: Transformer.Accumulating[[A] =>> Either[List[String], A], A, Type] = make(_).left.map(_ :: Nil)

  extension (self: Type) def value: A = self
}
