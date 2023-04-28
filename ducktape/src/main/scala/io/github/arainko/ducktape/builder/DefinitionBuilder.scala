package io.github.arainko.ducktape.builder

import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.fallible.{ FallibleTransformer, Mode }
import io.github.arainko.ducktape.internal.macros.*

import scala.deriving.Mirror

final class DefinitionBuilder[Source, Dest] {
  def fallible[F[+x], M <: Mode[F]](using M): DefinitionBuilder.Fallible[F, M, Source, Dest] =
    DefinitionBuilder.Fallible[F, M, Source, Dest]

  inline def build(inline config: BuilderConfig[Source, Dest]*): Transformer[Source, Dest] =
    from => Transformations.transformConfigured(from, config*)
}

object DefinitionBuilder {

  final class Fallible[F[+x], M <: Mode[F], Source, Dest] private[ducktape] (using private val F: M) {
    inline def build(
      inline config: FallibleBuilderConfig[F, Source, Dest] | BuilderConfig[Source, Dest]*
    )(using
      Source: Mirror.ProductOf[Source],
      Dest: Mirror.ProductOf[Dest]
    ): Transformer.Fallible[F, Source, Dest] =
      new {
        def transform(value: Source): F[Dest] =
          inline F match {
            case given Mode.Accumulating[F] =>
              Transformations.transformAccumulatingConfigured[F, Source, Dest](value, config*)
            case given Mode.FailFast[F] =>
              Transformations.transformFailFastConfigured[F, Source, Dest](value, config*)
            case other =>
              Errors.cannotDetermineTransformationMode
          }
      }
  }
}
