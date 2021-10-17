package io.github.arainko.ducktape.internal

final case class Both[A, B](first: A, second: B)

object Both {
  given [A, B](using a: A, b: B): Both[A, B] = Both(a, b)
}