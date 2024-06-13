package io.github.arainko.ducktape

import io.github.arainko.ducktape.internal.{ FallibleTransformations, TotalTransformations }

extension [Source](source: Source) {
  inline def to[Dest]: Dest = TotalTransformations.between[Source, Dest](source, "transformation")

  def into[Dest]: AppliedBuilder[Source, Dest] = AppliedBuilder[Source, Dest](source)

  transparent inline def via[Func](inline function: Func): Any =
    TotalTransformations.viaInferred[Source, Func](source, function)

  transparent inline def intoVia[Func](inline function: Func): Any =
    AppliedViaBuilder.create(source, function)
}

extension [F[+x], Source](value: Source)(using F: Mode[F]) {
  inline def fallibleTo[Dest]: F[Dest] = FallibleTransformations.between[F, Source, Dest](value, F, "transformation")

  transparent inline def fallibleVia[Func](inline function: Func): F[Any] =
    FallibleTransformations.viaInferred[F, Source, Func](value, function, F)
}

case class Costam(int: Int, str: String)
case class Costam2(str: String, int: Int)

object test {
  Costam(1, "").to[Costam2]
}
