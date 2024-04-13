package io.github.arainko.ducktape.internal

import scala.collection.mutable.Builder

private[ducktape] opaque type Accumulator[A] = Builder[A, List[A]]

private[ducktape] object Accumulator {

  def use[A]: [B <: Tuple] => (f: Accumulator[A] ?=> B) => List[A] *: B =
    [B <: Tuple] =>
      (f: Accumulator[A] ?=> B) => {
        val builder = List.newBuilder[A]
        val result = f(using builder)
        builder.result() *: result
    }

  def append[A](value: A)(using acc: Accumulator[A]): A = {
    acc.addOne(value)
    value
  }

  def appendAll[A, B <: Iterable[A]](values: B)(using acc: Accumulator[A]): B = {
    acc.addAll(values)
    values
  }
}
