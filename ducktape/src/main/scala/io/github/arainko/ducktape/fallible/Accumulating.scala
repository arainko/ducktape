package io.github.arainko.ducktape.fallible

import io.github.arainko.ducktape.Transformer
import io.github.arainko.ducktape.fallible.Accumulating.Support
import io.github.arainko.ducktape.internal.macros.*

import scala.collection.Factory
import scala.deriving.Mirror
import scala.annotation.implicitNotFound

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

  given betweenCollections[F[+x], Source, Dest, SourceColl[x] <: Iterable[x], DestColl[x] <: Iterable[x]](using
    transformer: Accumulating[F, Source, Dest],
    F: Support[F],
    factory: Factory[Dest, DestColl[Dest]]
  ): Accumulating[F, SourceColl[Source], DestColl[Dest]] =
    new {
      def transform(value: SourceColl[Source]): F[DestColl[Dest]] = F.traverseCollection(value)
    }

  @implicitNotFound("""An instance of Transformer.Accumulating.Support[F] needs to be explicitly put in implicit scope to enable error accumulating transformations.
For example:
  private given Transformer.Accumulating.Support[[A] =>> Either[List[String], A]] = Transformer.Accumulating.Support.either[String, List]  
""")
  trait Support[F[+x]] {
    def pure[A](value: A): F[A]
    def map[A, B](fa: F[A], f: A => B): F[B]
    def product[A, B](fa: F[A], fb: F[B]): F[(A, B)]

    // Kind of a bummer this has to exist, the 'foldLeft' and 'product' based implementation either blew up
    // the stack or took to long to finish for very long collections...
    def traverseCollection[A, B, AColl[x] <: Iterable[x], BColl[x] <: Iterable[x]](collection: AColl[A])(using
      transformer: Accumulating[F, A, B],
      factory: Factory[B, BColl[B]]
    ): F[BColl[B]]
  }

  object Support {

    def either[E, Coll[x] <: Iterable[x]](using
      errorCollFactory: Factory[E, Coll[E]]
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
              val builder = errorCollFactory.newBuilder
              val accumulated = builder ++= errorsA ++= errorsB
              Left(accumulated.result())
          }

        // Inspired by chimney's implementation: https://github.com/scalalandio/chimney/blob/53125c0a55479763157909ef920e11f5b487b182/chimney/src/main/scala/io/scalaland/chimney/TransformerFSupport.scala#L153
        override def traverseCollection[A, B, AColl[x] <: Iterable[x], BColl[x] <: Iterable[x]](collection: AColl[A])(using
          transformer: Accumulating[[A] =>> Either[Coll[E], A], A, B],
          factory: Factory[B, BColl[B]]
        ): Either[Coll[E], BColl[B]] = {
          val accumulatedErrors = errorCollFactory.newBuilder
          val accumulatedSuccesses = factory.newBuilder
          var isErroredOut = false

          collection.foreach { elem =>
            transformer.transform(elem) match {
              case Left(errors) =>
                accumulatedErrors.addAll(errors)
                if (isErroredOut == false) {
                  isErroredOut = true
                  accumulatedSuccesses.clear()
                }
              case Right(value) =>
                if (isErroredOut == false) {
                  accumulatedSuccesses.addOne(value)
                }
            }
          }

          if (isErroredOut) Left(accumulatedErrors.result()) else Right(accumulatedSuccesses.result())
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
