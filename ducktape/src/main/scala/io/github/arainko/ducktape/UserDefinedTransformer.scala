package io.github.arainko.ducktape

@FunctionalInterface
trait UserDefinedTransformer[-A, +B] {
  def transform(value: A): B
}
