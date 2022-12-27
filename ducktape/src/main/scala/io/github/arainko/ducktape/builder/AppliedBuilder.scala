package io.github.arainko.ducktape.builder

import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.builder.AppliedBuilder.FailFast
import io.github.arainko.ducktape.internal.macros.*

import scala.deriving.Mirror

final class AppliedBuilder[Source, Dest](appliedTo: Source) {

  def failFast[F[+x]]: FailFast[F, Source, Dest] =
    AppliedBuilder.FailFast[F, Source, Dest](appliedTo)

  def accumulating[F[+x]]: AppliedBuilder.Accumulating[F, Source, Dest] =
    AppliedBuilder.Accumulating[F, Source, Dest](appliedTo)

  inline def transform(inline config: BuilderConfig[Source, Dest]*): Dest =
    Transformations.transformConfigured(appliedTo, config*)

}

object AppliedBuilder {

  final class FailFast[F[+x], Source, Dest] private[ducktape] (private val source: Source) {
    inline def transform(
      inline config: FallibleBuilderConfig[F, Source, Dest] | BuilderConfig[Source, Dest]*
    )(using F: Transformer.FailFast.Support[F], Source: Mirror.ProductOf[Source], Dest: Mirror.ProductOf[Dest]): F[Dest] =
      Transformations.transformFailFastConfigured[F, Source, Dest](source, config*)
  }

  final class Accumulating[F[+x], Source, Dest] private[ducktape] (private val source: Source) {
    inline def transform(
      inline config: FallibleBuilderConfig[F, Source, Dest] | BuilderConfig[Source, Dest]*
    )(using F: Transformer.Accumulating.Support[F], Source: Mirror.ProductOf[Source], Dest: Mirror.ProductOf[Dest]): F[Dest] =
      Transformations.transformAccumulatingConfigured[F, Source, Dest](source, config*)
  }

}
