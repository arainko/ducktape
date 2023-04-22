package io.github.arainko.ducktape.fallible.instances

import scala.deriving.Mirror
import io.github.arainko.ducktape.Transformer
import io.github.arainko.ducktape.internal.macros.DerivedTransformers
import scala.collection.Factory

transparent trait FailFastInstances extends LowPriorityFailFastInstances {
  inline given derived[F[+x], Source, Dest](using
    Source: Mirror.ProductOf[Source],
    Dest: Mirror.ProductOf[Dest],
    F: Transformer.FailFast.Support[F]
  ): Transformer.FailFast[F, Source, Dest] = DerivedTransformers.failFastProduct[F, Source, Dest]

  given betweenOptions[F[+x], Source, Dest](using
    transformer: Transformer.FailFast[F, Source, Dest],
    F: Transformer.FailFast.Support[F]
  ): Transformer.FailFast[F, Option[Source], Option[Dest]] =
    new {
      def transform(value: Option[Source]): F[Option[Dest]] =
        value.fold(F.pure(None))(source => F.map(transformer.transform(source), Some.apply))
    }

  given betweenNonOptionOption[F[+x], Source, Dest](using
    transformer: Transformer.FailFast[F, Source, Dest],
    F: Transformer.FailFast.Support[F]
  ): Transformer.FailFast[F, Source, Option[Dest]] =
    new {
      def transform(value: Source): F[Option[Dest]] = F.map(transformer.transform(value), Some.apply)
    }

  given betweenCollections[F[+x], Source, Dest, SourceColl[x] <: Iterable[x], DestColl[x] <: Iterable[x]](using
    transformer: Transformer.FailFast[F, Source, Dest],
    F: Transformer.FailFast.Support[F],
    factory: Factory[Dest, DestColl[Dest]]
  ): Transformer.FailFast[F, SourceColl[Source], DestColl[Dest]] =
    new {
      def transform(value: SourceColl[Source]): F[DestColl[Dest]] = F.traverseCollection(value)
    }
}

transparent trait LowPriorityFailFastInstances {
  given partialFromTotal[F[+x], Source, Dest](using
    total: Transformer[Source, Dest],
    F: Transformer.FailFast.Support[F]
  ): Transformer.FailFast[F, Source, Dest] =
    new {
      def transform(value: Source): F[Dest] = F.pure(total.transform(value))
    }
}
