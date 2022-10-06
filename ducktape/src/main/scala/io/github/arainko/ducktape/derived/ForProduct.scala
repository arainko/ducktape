package io.github.arainko.ducktape.derived

import io.github.arainko.ducktape.Transformer

sealed abstract class ForProduct[Source, Dest] extends Transformer[Source, Dest]

object ForProduct {
  private[ducktape] def make[Source, Dest](f: Source => Dest): ForProduct[Source, Dest] =
    new {
      def transform(from: Source): Dest = f(from)
    }
}