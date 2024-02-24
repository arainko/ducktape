package io.github.arainko.ducktape

import io.github.arainko.ducktape
import io.github.arainko.ducktape.Transformer.Fallible
import io.github.arainko.ducktape.*

package fallible {
  type FallibleTransformer[F[+x], Source, Dest] = Transformer.Fallible[F, Source, Dest]
  val FallibleTransformer: Fallible.type = Transformer.Fallible

  type Mode[F[+x]] = io.github.arainko.ducktape.Mode[F]
  val Mode: ducktape.Mode.type = io.github.arainko.ducktape.Mode
}
