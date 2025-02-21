package io.github.arainko.ducktape

import scala.annotation.compileTimeOnly

type Regional[A]

object Regional {
  extension [F[a, b] <: (Case[a, b] | Field[a, b]), A, B, C](self: F[A, B] & Regional[C]) {
    @compileTimeOnly(".regional is only usable as field configuration for transformations")
    def regional[DestFieldTpe](selector: Selector ?=> C => DestFieldTpe): F[A, B] = ???
  }
}

type Local[A]

object Local {
  extension [F[a, b] <: (Case[a, b] | Field[a, b]), A, B, C](self: F[A, B] & Local[C]) {
    @compileTimeOnly(".local is only usable as field configuration for transformations")
    def local[DestFieldTpe](selector: Selector ?=> C => DestFieldTpe): F[A, B] = ???
  }
}

type TypeSpecific

object TypeSpecific {
  extension [F[a, b] <: (Case[a, b] | Field[a, b]), A, B, C](self: F[A, B] & Local[C]) {
    @compileTimeOnly(".typeSpecific is only usable as field configuration for transformations")
    def typeSpecific[Tpe]: F[A, B] = ???
  }
}
