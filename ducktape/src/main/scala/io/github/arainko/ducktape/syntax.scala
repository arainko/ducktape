package io.github.arainko.ducktape

import io.github.arainko.ducktape.builder.applied.*

extension [From](value: From)
  transparent inline def into[To] =
    AppliedTransformerBuilder.create[From, To](value)

  def to[To](using Transformer[From, To]): To = Transformer[From, To].transform(value)

end extension
