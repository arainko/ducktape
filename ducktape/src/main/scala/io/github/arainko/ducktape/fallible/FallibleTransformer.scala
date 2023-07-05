package io.github.arainko.ducktape.fallible

import io.github.arainko.ducktape.Transformer
import io.github.arainko.ducktape.fallible.Mode.{ Accumulating, FailFast }
import io.github.arainko.ducktape.internal.macros.DerivedTransformers

import scala.collection.Factory
import scala.deriving.Mirror

trait FallibleTransformer[F[+x], Source, Dest] {
  def transform(value: Source): F[Dest]
}

object FallibleTransformer extends LowPriorityAccumulatingInstances {
  inline given betweenProductsAccumulating[F[+x], Source, Dest](using
    Source: Mirror.ProductOf[Source],
    Dest: Mirror.ProductOf[Dest],
    F: Mode.Accumulating[F]
  ): FallibleTransformer[F, Source, Dest] = DerivedTransformers.accumulatingProduct[F, Source, Dest]

  inline given betweenProductsFailFast[F[+x], Source, Dest](using
    Source: Mirror.ProductOf[Source],
    Dest: Mirror.ProductOf[Dest],
    F: Mode.FailFast[F]
  ): FallibleTransformer[F, Source, Dest] = DerivedTransformers.failFastProduct[F, Source, Dest]

  inline given betweenCoproductsAccumulating[F[+x], Source, Dest](using
    Source: Mirror.SumOf[Source],
    Dest: Mirror.SumOf[Dest],
    F: Mode.Accumulating[F]
  ): FallibleTransformer[F, Source, Dest] = DerivedTransformers.accumulatingCoproduct[F, Source, Dest]

  given betweenOptions[F[+x], Source, Dest](using
    transformer: FallibleTransformer[F, Source, Dest],
    F: Mode[F]
  ): FallibleTransformer[F, Option[Source], Option[Dest]] =
    new {
      def transform(value: Option[Source]): F[Option[Dest]] =
        value.fold(F.pure(None))(source => F.map(transformer.transform(source), Some.apply))
    }

  given betweenNonOptionOption[F[+x], Source, Dest](using
    transformer: FallibleTransformer[F, Source, Dest],
    F: Mode[F]
  ): FallibleTransformer[F, Source, Option[Dest]] =
    new {
      def transform(value: Source): F[Option[Dest]] = F.map(transformer.transform(value), Some.apply)
    }

  given betweenCollections[F[+x], Source, Dest, SourceColl[x] <: Iterable[x], DestColl[x] <: Iterable[x]](using
    transformer: FallibleTransformer[F, Source, Dest],
    F: Mode[F],
    factory: Factory[Dest, DestColl[Dest]]
  ): FallibleTransformer[F, SourceColl[Source], DestColl[Dest]] =
    new {
      def transform(value: SourceColl[Source]): F[DestColl[Dest]] = F.traverseCollection(value)
    }

  given betweenMaps[F[+x], SourceKey, SourceValue, DestKey, DestValue, SourceMap[k, v] <: Map[k, v], DestMap[k, v] <: Map[k, v]](
    using
    keyTransformer: FallibleTransformer[F, SourceKey, DestKey],
    valueTransformer: FallibleTransformer[F, SourceValue, DestValue],
    factory: Factory[(DestKey, DestValue), DestMap[DestKey, DestValue]],
    F: Mode[F]
  ): FallibleTransformer[F, SourceMap[SourceKey, SourceValue], DestMap[DestKey, DestValue]] =
    new {
      def transform(value: SourceMap[SourceKey, SourceValue]): F[DestMap[DestKey, DestValue]] = {
        given FallibleTransformer[F, (SourceKey, SourceValue), (DestKey, DestValue)] =
          new {
            def transform(value: (SourceKey, SourceValue)): F[(DestKey, DestValue)] = {
              val transformedKey = keyTransformer.transform(value._1)
              F match {
                case acc: Accumulating[F] =>
                  val transformedValue = valueTransformer.transform(value._2)
                  acc.product(transformedKey, transformedValue)
                case ff: FailFast[F] =>
                  ff.flatMap(transformedKey, key => ff.map(valueTransformer.transform(value._2), value => key -> value))
              }
            }
          }
        F.map(F.traverseCollection[(SourceKey, SourceValue), (DestKey, DestValue), Iterable, Iterable](value), _.to(factory))
      }
    }
}

transparent trait LowPriorityAccumulatingInstances {
  given fallibleFromTotal[F[+x], Source, Dest](using
    total: Transformer[Source, Dest],
    F: Mode[F]
  ): FallibleTransformer[F, Source, Dest] =
    new {
      def transform(value: Source): F[Dest] = F.pure(total.transform(value))
    }
}
