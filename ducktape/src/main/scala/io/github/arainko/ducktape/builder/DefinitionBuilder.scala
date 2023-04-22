package io.github.arainko.ducktape.builder

import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.internal.macros.*

import scala.compiletime.*
import scala.deriving.Mirror

final class DefinitionBuilder[Source, Dest] {
  def accumulating[F[+x]](using Transformer.Accumulating.Support[F]): DefinitionBuilder.Accumulating[F, Source, Dest] = DefinitionBuilder.Accumulating[F, Source, Dest]

  def failFast[F[+x]](using Transformer.FailFast.Support[F]): DefinitionBuilder.FailFast[F, Source, Dest] = DefinitionBuilder.FailFast[F, Source, Dest]

  inline def build(inline config: BuilderConfig[Source, Dest]*): Transformer[Source, Dest] =
    from => Transformations.transformConfigured(from, config*)
}

object DefinitionBuilder {

  final class FailFast[F[+x], Source, Dest] private[ducktape] (using private val F: Transformer.FailFast.Support[F]) {
    inline def build(
      inline config: FallibleBuilderConfig[F, Source, Dest] | BuilderConfig[Source, Dest]*
    )(using
      Source: Mirror.ProductOf[Source],
      Dest: Mirror.ProductOf[Dest]
    ): Transformer.FailFast[F, Source, Dest] =
      new {
        def transform(value: Source): F[Dest] = Transformations.transformFailFastConfigured[F, Source, Dest](value, config*)
      }
  }

  final class Accumulating[F[+x], Source, Dest] private[ducktape] (using private val F: Transformer.Accumulating.Support[F]) {
    inline def build(
      inline config: FallibleBuilderConfig[F, Source, Dest] | BuilderConfig[Source, Dest]*
    )(using
      Source: Mirror.ProductOf[Source],
      Dest: Mirror.ProductOf[Dest]
    ): Transformer.Accumulating[F, Source, Dest] =
      new {
        def transform(value: Source): F[Dest] = Transformations.transformAccumulatingConfigured[F, Source, Dest](value, config*)
      }
  }
}
