package io.github.arainko.ducktape

import io.github.arainko.ducktape.builder.*
import io.github.arainko.ducktape.function.*
import io.github.arainko.ducktape.internal.macros.*
import io.github.arainko.ducktape.internal.modules.*

import scala.annotation.experimental
import scala.deriving.Mirror

extension [F[+x], Source](value: Source)(using F: Transformer.Accumulating.Support[F]) {
  def accumulatingTo[Dest](using transformer: Transformer.Accumulating[F, Source, Dest]): F[Dest] =
    transformer.transform(value)

  inline def accumulatingVia[Func](inline function: Func)(using
    Func: FunctionMirror[Func]
  )(using Source: Mirror.ProductOf[Source]): F[Func.Return] =
    Transformations.accumulatingVia[F, Source, Func](function)(value)
}
