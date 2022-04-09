package io.github.arainko.ducktape

import io.github.arainko.ducktape.Builder.Applied

extension [From](value: From) {
  def into[To]: Applied[From, To, EmptyTuple] =
    Builder.applied[From, To](value)

  def to[To](using Transformer[From, To]): To = Transformer[From, To].transform(value)
}
