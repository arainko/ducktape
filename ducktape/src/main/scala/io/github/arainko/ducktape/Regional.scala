package io.github.arainko.ducktape

import scala.annotation.compileTimeOnly

type Regional

object Regional {
  extension [F[a, b] <: (Case[b, a] | Field[a, b]), A, B](self: F[A, B] & Regional) {
    @compileTimeOnly(".regional is only usable as field configuration for transformations")
    def regional[DestFieldTpe](selector: Selector ?=> B => DestFieldTpe): F[A, B] = ???
  }
}
