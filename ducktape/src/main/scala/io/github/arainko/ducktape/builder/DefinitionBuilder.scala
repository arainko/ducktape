package io.github.arainko.ducktape.builder

import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.internal.macros.*

import scala.compiletime.*
import scala.deriving.Mirror
import io.github.arainko.ducktape.fallible.Mode
import io.github.arainko.ducktape.fallible.FallibleTransformer
import io.github.arainko.ducktape.fallible.Mode.FailFast

final class DefinitionBuilder[Source, Dest] {
  def fallible[F[+x], M <: Mode[F]](using M): DefinitionBuilder.Fallible[F, Source, Dest, M] = 
    DefinitionBuilder.Fallible[F, Source, Dest, M]

  // def failFast[F[+x]](using Transformer.FailFast.Support[F]): DefinitionBuilder.FailFast[F, Source, Dest] = DefinitionBuilder.FailFast[F, Source, Dest]

  inline def build(inline config: BuilderConfig[Source, Dest]*): Transformer[Source, Dest] =
    from => Transformations.transformConfigured(from, config*)
}

object DefinitionBuilder {

  // final class FailFast[F[+x], Source, Dest] private[ducktape] (using private val F: Transformer.FailFast.Support[F]) {
  //   inline def build(
  //     inline config: FallibleBuilderConfig[F, Source, Dest] | BuilderConfig[Source, Dest]*
  //   )(using
  //     Source: Mirror.ProductOf[Source],
  //     Dest: Mirror.ProductOf[Dest]
  //   ): Transformer.FailFast[F, Source, Dest] =
  //     new {
  //       def transform(value: Source): F[Dest] = Transformations.transformFailFastConfigured[F, Source, Dest](value, config*)
  //     }
  // }

  final class Fallible[F[+x], Source, Dest, M <: Mode[F]] private[ducktape] (using private val F: M) {
    inline def build(
      inline config: FallibleBuilderConfig[F, Source, Dest] | BuilderConfig[Source, Dest]*
    )(using
      Source: Mirror.ProductOf[Source],
      Dest: Mirror.ProductOf[Dest]
    ): FallibleTransformer[F, Source, Dest] =
      new {
        def transform(value: Source): F[Dest] = 
          inline F match {
            case given Mode.Accumulating[F] => 
              Transformations.transformAccumulatingConfigured[F, Source, Dest](value, config*)
            case given Mode.FailFast[F] =>
              Transformations.transformFailFastConfigured[F, Source, Dest](value, config*)
          }
      }
  }
}
