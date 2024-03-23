package io.github.arainko.ducktape

case class ContextualError[E](path: String, error: E)

case class Pathed[A](path: String, value: A)

trait ExperimentalMode[F[+x]] {
  final type Self[+x] = F[x] 

  type Input[A] <: Pathed[F[A]] | F[A]

  def pure[A](value: A): F[A]

  def map[A, B](fa: F[A], f: A => B): F[B]

  def flatMap[A, B](fa: Input[A], f: A => F[B]): F[B]
}

object ExperimentalMode {
  class Ethr[E] extends ExperimentalMode[[a] =>> Either[ContextualError[E], a]] {

    final type Input[A] = Pathed[Self[A]]

    override def pure[A](value: A): Either[ContextualError[E], A] = Right(value)

    override def map[A, B](fa: Self[A], f: A => B): Either[ContextualError[E], B] = 
      fa.map(f)

    override def flatMap[A, B](fa: Input[A], f: A => Either[ContextualError[E], B]): Either[ContextualError[E], B] = 
      fa.value.flatMap(f).left.map(err => err.copy(path = fa.path))

    
  }
}
