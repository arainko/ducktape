package io.github.arainko.ducktape.fallible

import io.github.arainko.ducktape.Transformer
import io.github.arainko.ducktape.internal.macros.DerivedTransformers

import scala.collection.Factory
import scala.deriving.Mirror

trait FailFast[F[+x], Source, Dest] {
  def transform(value: Source): F[Dest]
}

object FailFast extends LowPriorityFailFastInstances {

  def apply[F[+x], Source, Dest](using transformer: FailFast[F, Source, Dest]): FailFast[F, Source, Dest] = transformer

  inline given derived[F[+x], Source, Dest](using
    Source: Mirror.ProductOf[Source],
    Dest: Mirror.ProductOf[Dest],
    F: FailFast.Support[F]
  ): FailFast[F, Source, Dest] = DerivedTransformers.failFastProduct[F, Source, Dest]

  given betweenOptions[F[+x], Source, Dest](using
    transformer: FailFast[F, Source, Dest],
    F: Support[F]
  ): FailFast[F, Option[Source], Option[Dest]] =
    new {
      def transform(value: Option[Source]): F[Option[Dest]] =
        value.fold(F.pure(None))(source => F.map(transformer.transform(source), Some.apply))
    }

  given betweenNonOptionOption[F[+x], Source, Dest](using
    transformer: FailFast[F, Source, Dest],
    F: Support[F]
  ): FailFast[F, Source, Option[Dest]] =
    new {
      def transform(value: Source): F[Option[Dest]] = F.map(transformer.transform(value), Some.apply)
    }

  given betweenCollections[F[+x], Source, Dest, SourceColl[x] <: Iterable[x], DestColl[x] <: Iterable[x]](using
    transformer: FailFast[F, Source, Dest],
    F: Support[F],
    factory: Factory[Dest, DestColl[Dest]]
  ): FailFast[F, SourceColl[Source], DestColl[Dest]] =
    new {
      def transform(value: SourceColl[Source]): F[DestColl[Dest]] = F.traverseCollection(value)
    }

  trait Support[F[+x]] {
    def pure[A](value: A): F[A]
    def map[A, B](fa: F[A], f: A => B): F[B]
    def flatMap[A, B](fa: F[A], f: A => F[B]): F[B]

    // Kind of a bummer this has to exist, the 'foldLeft' and 'product' based implementation either blew up
    // the stack or took to long to finish for very long collections...
    def traverseCollection[A, B, AColl[x] <: Iterable[x], BColl[x] <: Iterable[x]](collection: AColl[A])(using
      transformer: FailFast[F, A, B],
      factory: Factory[B, BColl[B]]
    ): F[BColl[B]]
  }

  object Support {
    given optionFailFastSupport: Support[Option] =
      new {
        def pure[A](value: A): Option[A] = Some(value)
        def map[A, B](fa: Option[A], f: A => B): Option[B] = fa.map(f)
        def flatMap[A, B](fa: Option[A], f: A => Option[B]): Option[B] = fa.flatMap(f)

        def traverseCollection[A, B, AColl[x] <: Iterable[x], BColl[x] <: Iterable[x]](
          collection: AColl[A]
        )(using transformer: FailFast[Option, A, B], factory: Factory[B, BColl[B]]): Option[BColl[B]] = {
          var isErroredOut = false
          val resultBuilder = factory.newBuilder
          val iterator = collection.iterator
          while (iterator.hasNext && !isErroredOut) {
            transformer.transform(iterator.next()) match {
              case None => 
                isErroredOut = true
                resultBuilder.clear()
              case Some(value) =>
                resultBuilder += value
            }
          }

          if (isErroredOut) None else Some(resultBuilder.result())
        }
      }

    given eitherFailFastSupport[E]: Support[[A] =>> Either[E, A]] =
      new {
        def pure[A](value: A): Either[E, A] = Right(value)
        def map[A, B](fa: Either[E, A], f: A => B): Either[E, B] = fa.map(f)
        def flatMap[A, B](fa: Either[E, A], f: A => Either[E, B]): Either[E, B] = fa.flatMap(f)
        def traverseCollection[A, B, AColl[x] <: Iterable[x], BColl[x] <: Iterable[x]](
          collection: AColl[A]
        )(using transformer: FailFast[[A] =>> Either[E, A], A, B], factory: Factory[B, BColl[B]]): Either[E, BColl[B]] = {
          var error: Left[E, Nothing] = null
          def isErroredOut = !(error eq null)

          val resultBuilder = factory.newBuilder
          val iterator = collection.iterator
          while (iterator.hasNext && !isErroredOut) {
            transformer.transform(iterator.next()) match {
              case err @ Left(_) => 
                error = err.asInstanceOf[Left[E, Nothing]]
                resultBuilder.clear()
              case Right(value) =>
                resultBuilder += value
            }
          }

          if (isErroredOut) error else Right(resultBuilder.result())
        }
      }
  }
}

sealed trait LowPriorityFailFastInstances {
  given partialFromTotal[F[+x], Source, Dest](using
    total: Transformer[Source, Dest],
    F: FailFast.Support[F]
  ): FailFast[F, Source, Dest] =
    new {
      def transform(value: Source): F[Dest] = F.pure(total.transform(value))
    }
}
