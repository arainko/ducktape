package io.github.arainko.ducktape.internal

private[ducktape] opaque type Depth <: Int = Int

private[ducktape] object Depth {
  val zero: Depth = 0

  def current(using depth: Depth): Depth = depth

  extension (self: Depth) {
    def incremented: Depth = self + 1
  }
}
