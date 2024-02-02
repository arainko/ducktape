package io.github.arainko.ducktape

import io.github.arainko.ducktape.internal.TotalTransformations
import io.github.arainko.ducktape.internal.FallibleTransformations

final class AppliedBuilder[Source, Dest] private[ducktape] (value: Source) {
  inline def transform(inline config: Field[Source, Dest] | Case[Source, Dest]*): Dest =
    TotalTransformations.between[Source, Dest](value, "transformation", config*)

  def fallible[F[+x], M <: Mode[F]](using M): AppliedBuilder.Fallible[F, M, Source, Dest] =
    AppliedBuilder.Fallible[F, M, Source, Dest](value)
}

object AppliedBuilder {

  final class Fallible[F[+x], M <: Mode[F], Source, Dest] private[ducktape] (source: Source)(using F: M) {

    inline def transform(
      inline config: Field.Fallible[F, Source, Dest] | Case.Fallible[F, Source, Dest]*
    ): F[Dest] =
      FallibleTransformations.between[F, Source, Dest](source, F, "transformation", config*)
  }
}
