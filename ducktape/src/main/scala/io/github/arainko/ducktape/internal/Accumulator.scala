package io.github.arainko.ducktape.internal

import scala.collection.mutable.Builder
import scala.compiletime.*

private[ducktape] opaque type Accumulator[A] = Builder[A, List[A]]

private[ducktape] object Accumulator {

  // type Expand[T <: Tuple, Ret <: Tuple] = 
  //   T match {
  //     case EmptyTuple => Ret
  //     case h *: t => Accumulator[h] => Expand[t,  List[h] *: Ret] 
  //   }

  // inline def expand[T <: Tuple]: Expand[T, EmptyTuple] = 
  //   inline erasedValue[T] match {
  //     case _: EmptyTuple => ???
  //     case _: (h *: t) => ???
  //   }

  // val cosn: Accumulator[Int] => Accumulator[String] => Accumulator[Double] => (List[Double], List[String], List[Int]) =
  //   expand[(Int, String, Double)]

  // val cos2: Accumulator[Int] => Accumulator[String] => Accumulator[Double] => (List[Double], List[String], List[Int]) =
  //   accInt => accStr => accDouble => (accDouble.result(), accStr.result(), accInt.result())

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
