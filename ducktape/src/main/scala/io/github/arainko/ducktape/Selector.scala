package io.github.arainko.ducktape

sealed trait Selector {
  extension [A](self: A) def at[B <: A]: B

  extension [Elem](self: Iterable[Elem] | Option[Elem]) def element: Elem

  extension [Elem, F[+x]](using Mode[F])(self: F[Elem]) def element: Elem
}
