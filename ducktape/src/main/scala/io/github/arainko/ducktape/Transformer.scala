package io.github.arainko.ducktape

@FunctionalInterface
trait Transformer[From, To] {
  def transform(from: From): To
}

object Transformer {
  given [A]: Transformer[A, A] = identity

  given [A, B](using transformer: Transformer[A, B]): Transformer[A, Option[B]] = 
    transformer.transform.andThen(Some.apply)(_)
}
