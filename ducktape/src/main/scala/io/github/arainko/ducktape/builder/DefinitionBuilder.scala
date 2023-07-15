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
      Source: Mirror.Of[Source],
      Dest: Mirror.Of[Dest]
    ): Transformer.Fallible[F, Source, Dest] =
      new {
        def transform(value: Source): F[Dest] =
          Transformations.transformConfiguredFallible[F, Source, Dest](value, config*)
      }
  }
}
