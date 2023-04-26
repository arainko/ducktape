package io.github.arainko.ducktape.fallible

import io.github.arainko.ducktape.*

import scala.annotation.implicitNotFound
import scala.collection.Factory

@implicitNotFound(
  """ducktape needs an instance of either Transformer.Mode.Accumulating[F] or Transformer.Mode.FailFast[F] in implicit scope to infer the wrapper type F and determine the mode of fallible transformations.

For example, if you want your fallible transformations to accumulate errors and to return an Either[List[String], A] for some type A you can do this:

private given Transformer.Mode.Accumulating[[A] =>> Either[List[String], A]] = 
  Transformer.Mode.Accumulating.either[String, List]
"""
)
sealed trait Mode[F[+x]] {
  def pure[A](value: A): F[A]
  def map[A, B](fa: F[A], f: A => B): F[B]
  def traverseCollection[A, B, AColl[x] <: Iterable[x], BColl[x] <: Iterable[x]](collection: AColl[A])(using
    transformer: FallibleTransformer[F, A, B],
    factory: Factory[B, BColl[B]]
  ): F[BColl[B]]
}

object Mode {
  trait Accumulating[F[+x]] extends Mode[F] {
    def product[A, B](fa: F[A], fb: F[B]): F[(A, B)]
  }

  object Accumulating {
    def either[E, Coll[x] <: Iterable[x]](using
      errorCollFactory: Factory[E, Coll[E]]
    ): Mode.Accumulating[[A] =>> Either[Coll[E], A]] =
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
          transformer: FallibleTransformer[[A] =>> Either[Coll[E], A], A, B],
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

  trait FailFast[F[+x]] extends Mode[F] {
    def flatMap[A, B](fa: F[A], f: A => F[B]): F[B]
  }

  object FailFast {
    val option: Mode.FailFast[Option] =
      new {
        def pure[A](value: A): Option[A] = Some(value)
        def map[A, B](fa: Option[A], f: A => B): Option[B] = fa.map(f)
        def flatMap[A, B](fa: Option[A], f: A => Option[B]): Option[B] = fa.flatMap(f)

        def traverseCollection[A, B, AColl[x] <: Iterable[x], BColl[x] <: Iterable[x]](
          collection: AColl[A]
        )(using transformer: FallibleTransformer[Option, A, B], factory: Factory[B, BColl[B]]): Option[BColl[B]] = {
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

    def either[E]: Mode.FailFast[[A] =>> Either[E, A]] =
      new {
        def pure[A](value: A): Either[E, A] = Right(value)
        def map[A, B](fa: Either[E, A], f: A => B): Either[E, B] = fa.map(f)
        def flatMap[A, B](fa: Either[E, A], f: A => Either[E, B]): Either[E, B] = fa.flatMap(f)
        def traverseCollection[A, B, AColl[x] <: Iterable[x], BColl[x] <: Iterable[x]](
          collection: AColl[A]
        )(using
          transformer: FallibleTransformer[[A] =>> Either[E, A], A, B],
          factory: Factory[B, BColl[B]]
        ): Either[E, BColl[B]] = {
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
