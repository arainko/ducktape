package io.github.arainko.ducktape

import scala.annotation.compileTimeOnly

sealed trait Selector {
  extension [A](self: A) def at[B <: A]: B

  extension [Elem](self: Iterable[Elem] | Option[Elem]) def element: Elem

  extension [Elem, F[+x]](using Mode[F])(self: F[Elem]) @compileTimeOnly("") def element: Elem
}
