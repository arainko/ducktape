package io.github.arainko.ducktape

sealed trait Selector {
  extension [A](self: A) def at[B <: A]: B
}

object Selector {
  type Of[A] = Selector ?=> A => Any 
}
