package io.github.arainko.ducktape.builder

import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.fallible.Mode
import io.github.arainko.ducktape.internal.macros.*

import scala.deriving.Mirror

final class AppliedBuilder[Source, Dest](appliedTo: Source) {

  def fallible[F[+x], M <: Mode[F]](using M): AppliedBuilder.Fallible[F, M, Source, Dest] =
    AppliedBuilder.Fallible[F, M, Source, Dest](appliedTo)

  inline def transform(inline config: BuilderConfig[Source, Dest]*): Dest =
    Transformations.transformConfigured(appliedTo, config*)
}

object AppliedBuilder {

  final class Fallible[F[+x], M <: Mode[F], Source, Dest] private[ducktape] (source: Source)(using F: M) {

    inline def transform(
      inline config: FallibleBuilderConfig[F, Source, Dest] | BuilderConfig[Source, Dest]*
    )(using Mirror.ProductOf[Source], Mirror.ProductOf[Dest]): F[Dest] =
      inline F match {
        case given Mode.Accumulating[F] =>
          Transformations.transformAccumulatingConfigured[F, Source, Dest](source, config*)
        case given Mode.FailFast[F] =>
          Transformations.transformFailFastConfigured[F, Source, Dest](source, config*)
        case other =>
          Errors.cannotDetermineTransformationMode
      }
  }
}
