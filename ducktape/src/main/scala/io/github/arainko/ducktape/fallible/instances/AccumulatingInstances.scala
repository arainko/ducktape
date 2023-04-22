package io.github.arainko.ducktape.fallible.instances

import io.github.arainko.ducktape.Transformer
import io.github.arainko.ducktape.internal.macros.DerivedTransformers
import scala.deriving.Mirror
import scala.collection.Factory

transparent trait AccumulatingInstances extends LowPriorityAccumulatingInstances {
  inline given derived[F[+x], Source, Dest](using
    Source: Mirror.ProductOf[Source],
    Dest: Mirror.ProductOf[Dest],
    F: Transformer.Accumulating.Support[F]
  ): Transformer.Accumulating[F, Source, Dest] = DerivedTransformers.accumulatingProduct[F, Source, Dest]

  given betweenOptions[F[+x], Source, Dest](using
    transformer: Transformer.Accumulating[F, Source, Dest],
    F: Transformer.Accumulating.Support[F]
  ): Transformer.Accumulating[F, Option[Source], Option[Dest]] =
    new {
      def transform(value: Option[Source]): F[Option[Dest]] =
        value.fold(F.pure(None))(source => F.map(transformer.transform(source), Some.apply))
    }

  given betweenNonOptionOption[F[+x], Source, Dest](using
    transformer: Transformer.Accumulating[F, Source, Dest],
    F: Transformer.Accumulating.Support[F]
  ): Transformer.Accumulating[F, Source, Option[Dest]] =
    new {
      def transform(value: Source): F[Option[Dest]] = F.map(transformer.transform(value), Some.apply)
    }

  given betweenCollections[F[+x], Source, Dest, SourceColl[x] <: Iterable[x], DestColl[x] <: Iterable[x]](using
    transformer: Transformer.Accumulating[F, Source, Dest],
    F: Transformer.Accumulating.Support[F],
    factory: Factory[Dest, DestColl[Dest]]
  ): Transformer.Accumulating[F, SourceColl[Source], DestColl[Dest]] =
    new {
      def transform(value: SourceColl[Source]): F[DestColl[Dest]] = F.traverseCollection(value)
    }
}

transparent trait LowPriorityAccumulatingInstances {
  given partialFromTotal[F[+x], Source, Dest](using
    total: Transformer[Source, Dest],
    F: Transformer.Accumulating.Support[F]
  ): Transformer.Accumulating[F, Source, Dest] =
    new {
      def transform(value: Source): F[Dest] = F.pure(total.transform(value))
    }
}
