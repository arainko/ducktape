package io.github.arainko.ducktape

import scala.annotation.implicitNotFound
import scala.collection.Factory

@implicitNotFound(
  """ducktape needs an instance of either Mode.Accumulating[F] or Mode.FailFast[F] in implicit scope to infer the wrapper type F and determine the mode of fallible transformations.
For example, if you want your fallible transformations to accumulate errors and to return an Either[List[String], A] for some type A you can do this:
private given Transformer.Mode.Either[String, List] with {}
"""
)
sealed trait Mode[F[+x]] {
  final type Self[+x] = F[x]

  def pure[A](value: A): F[A]
  def map[A, B](fa: F[A], f: A => B): F[B]
  def traverseCollection[A, B, AColl <: Iterable[A], BColl <: Iterable[B]](
    collection: AColl,
    transformation: A => F[B]
  )(using factory: Factory[B, BColl]): F[BColl]
}

object Mode {
  inline def current(using mode: Mode[?]): mode.type = mode
  extension [F[+x], M <: Mode[F]](self: M) {
    inline def locally[A](inline f: M ?=> A): A = f(using self)
  }

  trait Accumulating[F[+x]] extends Mode[F] {
    def product[A, B](fa: F[A], fb: F[B]): F[(A, B)]
  }

  object Accumulating {
    class Either[E, Coll[x] <: Iterable[x]](using errorCollFactory: Factory[E, Coll[E]])
        extends Mode.Accumulating[[A] =>> scala.Either[Coll[E], A]] {

      override def pure[A](value: A): scala.Either[Coll[E], A] = Right(value)

      override def map[A, B](fa: scala.Either[Coll[E], A], f: A => B): scala.Either[Coll[E], B] = fa.map(f)

      override def product[A, B](fa: scala.Either[Coll[E], A], fb: scala.Either[Coll[E], B]): scala.Either[Coll[E], (A, B)] =
        (fa, fb) match {
          case (Right(a), Right(b))      => Right(a -> b)
          case (Right(_), err @ Left(_)) => err.asInstanceOf[scala.Either[Coll[E], (A, B)]]
          case (err @ Left(_), Right(_)) => err.asInstanceOf[scala.Either[Coll[E], (A, B)]]
          case (Left(errorsA), Left(errorsB)) =>
            val builder = errorCollFactory.newBuilder
            val accumulated = builder ++= errorsA ++= errorsB
            Left(accumulated.result())
        }

      // Inspired by chimney's implementation: https://github.com/scalalandio/chimney/blob/53125c0a55479763157909ef920e11f5b487b182/chimney/src/main/scala/io/scalaland/chimney/TransformerFSupport.scala#L153
      override def traverseCollection[A, B, AColl <: Iterable[A], BColl <: Iterable[B]](
        collection: AColl,
        transformation: A => scala.Either[Coll[E], B]
      )(using
        factory: Factory[B, BColl]
      ): scala.Either[Coll[E], BColl] = {
        val accumulatedErrors = errorCollFactory.newBuilder
        val accumulatedSuccesses = factory.newBuilder
        var isErroredOut = false

        collection.foreach { elem =>
          transformation(elem) match {
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

    def either[E, Coll[x] <: Iterable[x]](using Factory[E, Coll[E]]): Mode.Accumulating.Either[E, Coll] =
      Accumulating.Either[E, Coll]
  }

  trait FailFast[F[+x]] extends Mode[F] {
    def flatMap[A, B](fa: F[A], f: A => F[B]): F[B]
  }

  object FailFast {
    class Either[E] extends Mode.FailFast[[A] =>> scala.Either[E, A]] {
      final def pure[A](value: A): scala.Either[E, A] = Right(value)

      final def map[A, B](fa: scala.Either[E, A], f: A => B): scala.Either[E, B] = fa.map(f)

      final def flatMap[A, B](fa: scala.Either[E, A], f: A => scala.Either[E, B]): scala.Either[E, B] = fa.flatMap(f)

      final def traverseCollection[A, B, AColl <: Iterable[A], BColl <: Iterable[B]](
        collection: AColl,
        transformation: A => scala.Either[E, B]
      )(using
        factory: Factory[B, BColl]
      ): scala.Either[E, BColl] = {
        var error: Left[E, Nothing] = null
        def isErroredOut = !(error eq null)

        val resultBuilder = factory.newBuilder
        val iterator = collection.iterator
        while (iterator.hasNext && !isErroredOut) {
          transformation(iterator.next()) match {
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

    class Option extends Mode.FailFast[scala.Option] {

      final def pure[A](value: A): scala.Option[A] = Some(value)

      final def map[A, B](fa: scala.Option[A], f: A => B): scala.Option[B] = fa.map(f)

      final def flatMap[A, B](fa: scala.Option[A], f: A => scala.Option[B]): scala.Option[B] = fa.flatMap(f)

      final def traverseCollection[A, B, AColl <: Iterable[A], BColl <: Iterable[B]](
        collection: AColl,
        transformation: A => scala.Option[B]
      )(using factory: Factory[B, BColl]): scala.Option[BColl] = {
        var isErroredOut = false
        val resultBuilder = factory.newBuilder
        val iterator = collection.iterator
        while (iterator.hasNext && !isErroredOut) {
          transformation(iterator.next()) match {
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

    val option: Mode.FailFast.Option = Mode.FailFast.Option()

    def either[E]: Mode.FailFast.Either[E] = Mode.FailFast.Either[E]
  }
}

object test extends App {
  import scala.util.chaining.*

  val t = (
    Option(1),
    Option(2),
    None
  )

  val res = Mode.FailFast.option.locally { 
    t.fallibleTo[Tuple.InverseMap[t.type, Mode.current.Self]]
  }

  println(res)
}
