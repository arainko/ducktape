package io.github.arainko.ducktape.fallible

import io.github.arainko.ducktape.Transformer
import io.github.arainko.ducktape.fallible.Accumulating.Support
import io.github.arainko.ducktape.internal.macros.*

import scala.collection.Factory
import scala.deriving.Mirror

trait Accumulating[F[+x], Source, Dest] {
  def transform(value: Source): F[Dest]
}

object Accumulating extends LowPriorityAccumulatingInstances {

  def apply[F[+x], Source, Dest](using transformer: Accumulating[F, Source, Dest]): Accumulating[F, Source, Dest] = transformer

  inline given derived[F[+x], Source, Dest](using
    Source: Mirror.ProductOf[Source],
    Dest: Mirror.ProductOf[Dest],
    F: Accumulating.Support[F]
  ): Accumulating[F, Source, Dest] = DerivedTransformers.accumulatingProduct[F, Source, Dest]

  given betweenOptions[F[+x], Source, Dest](using
    transformer: Accumulating[F, Source, Dest],
    F: Support[F]
  ): Accumulating[F, Option[Source], Option[Dest]] =
    new {
      def transform(value: Option[Source]): F[Option[Dest]] =
        value.fold(F.pure(None))(source => F.map(transformer.transform(source), Some.apply))
    }

  given betweenNonOptionOption[F[+x], Source, Dest](using
    transformer: Accumulating[F, Source, Dest],
    F: Support[F]
  ): Accumulating[F, Source, Option[Dest]] =
    new {
      def transform(value: Source): F[Option[Dest]] = F.map(transformer.transform(value), Some.apply)
    }

  // very-very naive impl, can probably lead to stack overflows given big enough collections and a data type that is not stack safe...
  given betweenCollections[F[+x], Source, Dest, SourceColl[x] <: Iterable[x], DestColl[x] <: Iterable[x]](using
    transformer: Accumulating[F, Source, Dest],
    F: Support[F],
    factory: Factory[Dest, DestColl[Dest]]
  ): Accumulating[F, SourceColl[Source], DestColl[Dest]] =
    new {
      def transform(value: SourceColl[Source]): F[DestColl[Dest]] = {
        val traversed = value.foldLeft(F.pure(factory.newBuilder)) { (builder, elem) =>
          F.map(F.product(builder, transformer.transform(elem)), _ += _)
        }
        F.map(traversed, _.result())
      }
    }

  trait Support[F[+x]] {
    def pure[A](value: A): F[A]
    def map[A, B](fa: F[A], f: A => B): F[B]
    def product[A, B](fa: F[A], fb: F[B]): F[(A, B)]
  }

  object Support {

    given eitherIterableAccumulatingSupport[E, Coll[x] <: Iterable[x]](using
      factory: Factory[E, Coll[E]]
    ): Support[[A] =>> Either[Coll[E], A]] =
      new {
        override def pure[A](value: A): Either[Coll[E], A] = Right(value)
        override def map[A, B](fa: Either[Coll[E], A], f: A => B): Either[Coll[E], B] = fa.map(f)
        override def product[A, B](fa: Either[Coll[E], A], fb: Either[Coll[E], B]): Either[Coll[E], (A, B)] =
          (fa, fb) match {
            case (Right(a), Right(b))      => Right(a -> b)
            case (Right(_), err @ Left(_)) => err.asInstanceOf[Either[Coll[E], (A, B)]]
            case (err @ Left(_), Right(_)) => err.asInstanceOf[Either[Coll[E], (A, B)]]
            case (Left(errorsA), Left(errorsB)) =>
              val builder = factory.newBuilder
              val accumulated = builder ++= errorsA ++= errorsB
              Left(accumulated.result())
          }
      }
  }
}

sealed trait LowPriorityAccumulatingInstances {
  given partialFromTotal[F[+x], Source, Dest](using
    total: Transformer[Source, Dest],
    F: Support[F]
  ): Accumulating[F, Source, Dest] =
    new {
      def transform(value: Source): F[Dest] = F.pure(total.transform(value))
    }
}
