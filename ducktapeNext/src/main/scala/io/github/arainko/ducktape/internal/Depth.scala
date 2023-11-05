package io.github.arainko.ducktape.internal

opaque type Depth <: Int = Int

object Depth {
  val zero: Depth = 0
  def current(using depth: Depth): Depth = depth
  def incremented(using depth: Depth): Depth = depth + 1
}
