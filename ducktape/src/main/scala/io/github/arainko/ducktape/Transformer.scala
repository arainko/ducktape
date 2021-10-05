package io.github.arainko.ducktape

trait Transformer[From, To] {
  def transform(from: From): To
}
