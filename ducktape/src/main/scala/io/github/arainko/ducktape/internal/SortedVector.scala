package io.github.arainko.ducktape.internal

private[ducktape] opaque type SortedVector[+A] <: Vector[A] = Vector[A]

private[ducktape] object SortedVector {
  def from[A: Ordering](vec: Vector[A]): SortedVector[A] = vec.sorted
}
