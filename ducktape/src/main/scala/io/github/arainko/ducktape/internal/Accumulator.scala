package io.github.arainko.ducktape.internal

import scala.collection.mutable.Builder

private[ducktape] opaque type Accumulator[A] = Builder[A, List[A]]

private[ducktape] object Accumulator {

  inline def use[A]: [B] => (f: Accumulator[A] ?=> B) => (List[A], B) =
    [B] =>
      (f: Accumulator[A] ?=> B) => {
        val builder = List.newBuilder[A]
        val result = f(using builder)
        builder.result() -> result
    }

  inline def append[A](value: A)(using acc: Accumulator[A]): A = {
    acc.addOne(value)
    value
  }
}
