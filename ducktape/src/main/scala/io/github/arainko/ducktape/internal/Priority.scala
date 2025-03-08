package io.github.arainko.ducktape.internal

private[ducktape] opaque type Priority <: Int = Int

private[ducktape] object Priority {

  def of(value: Int): Priority = value

  def allOf[F[_]](prios: F[Int]): F[Priority] = prios

  given ordering: Ordering[Priority] = Ordering.Int

}
