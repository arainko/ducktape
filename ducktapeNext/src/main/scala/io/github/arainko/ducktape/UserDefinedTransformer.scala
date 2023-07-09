package io.github.arainko.ducktape

trait UserDefinedTransformer[-Source, +Dest] {
  def transform(value: Source): Dest
}
