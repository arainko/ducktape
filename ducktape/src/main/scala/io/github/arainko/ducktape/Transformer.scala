package io.github.arainko.ducktape

import io.github.arainko.ducktape.DefinitionViaBuilder.PartiallyApplied
import io.github.arainko.ducktape.internal.Transformations
import io.github.arainko.ducktape.internal.FallibleTransformations

trait Transformer[Source, Dest] extends Transformer.Derived[Source, Dest]

object Transformer {
  inline given derive[Source, Dest]: Transformer.Derived[Source, Dest] = new {
    def transform(value: Source): Dest = Transformations.between[Source, Dest](value, "definition")
  }

  def define[Source, Dest]: DefinitionBuilder[Source, Dest] =
    DefinitionBuilder[Source, Dest]

  def defineVia[Source]: DefinitionViaBuilder.PartiallyApplied[Source] =
    DefinitionViaBuilder.create[Source]

  sealed trait Derived[Source, Dest] {
    def transform(value: Source): Dest
  }

  trait Fallible[F[+x], Source, Dest] extends Fallible.Derived[F, Source, Dest]

  object Fallible {
    sealed trait Derived[F[+x], Source, Dest] {
      def transform(source: Source): F[Dest]
    }

    inline given derive[F[+x], Source, Dest](using F: Mode.Accumulating[F]): Transformer.Fallible.Derived[F, Source, Dest] = new {
      def transform(source: Source): F[Dest] = FallibleTransformations.between[F, Source, Dest](source, F)
    }
  }
}
