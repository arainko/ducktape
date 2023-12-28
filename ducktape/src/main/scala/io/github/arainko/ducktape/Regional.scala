package io.github.arainko.ducktape

sealed trait Regional

object Regional {
  extension [F[a, b] <: (Case[b, a] | Field[a, b]), A, B](self: F[A, B] & Regional) {
    // @compileTimeOnly("woowow")
    def regional[DestFieldTpe](selector: Selector ?=> B => DestFieldTpe): F[A, B] = ???
  }
}
