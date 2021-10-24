package io.github.arainko.ducktape.internal

private[ducktape] final case class Both[A, B](first: A, second: B)

private[ducktape] object Both: 
  given [A, B](using a: A, b: B): Both[A, B] = Both(a, b)
  
end Both
