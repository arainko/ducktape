package io.github.arainko.ducktape

sealed trait Selector {
  extension [A](self: A) def at[B <: A]: B

  extension [Elem](self: Iterable[Elem] | Option[Elem]) def element: Elem

  extension [F[+x], Elem](self: F[Elem])(using Mode[F]) def element: Elem
}
