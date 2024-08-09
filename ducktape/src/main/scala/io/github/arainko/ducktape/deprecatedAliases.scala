package io.github.arainko.ducktape

import io.github.arainko.ducktape
import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.Transformer.Fallible

package fallible {
  @deprecated(message = "Use io.github.arainko.ducktape.Transformer.Fallible instead", since = "ducktape 0.2.0-M3")
  type FallibleTransformer[F[+x], Source, Dest] = Transformer.Fallible[F, Source, Dest]

  @deprecated(message = "Use io.github.arainko.ducktape.Transformer.Fallible instead", since = "ducktape 0.2.0-M3")
  val FallibleTransformer: Fallible.type = Transformer.Fallible

  @deprecated(message = "Use io.github.arainko.ducktape.Mode instead", since = "ducktape 0.2.0-M3")
  type Mode[F[+x]] = io.github.arainko.ducktape.Mode[F]

  @deprecated(message = "Use io.github.arainko.ducktape.Mode instead", since = "ducktape 0.2.0-M3")
  val Mode: ducktape.Mode.type = io.github.arainko.ducktape.Mode
}
