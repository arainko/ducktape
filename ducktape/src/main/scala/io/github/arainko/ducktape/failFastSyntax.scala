package io.github.arainko.ducktape

import io.github.arainko.ducktape.builder.*
import io.github.arainko.ducktape.function.*
import io.github.arainko.ducktape.internal.macros.*
import io.github.arainko.ducktape.internal.modules.*

import scala.annotation.experimental
import scala.deriving.Mirror
import scala.annotation.targetName
import scala.annotation.implicitNotFound

extension [F[+x], Source](value: Source)(using F: Transformer.FailFast.Support[F]) {
  def failFastTo[Dest](using transformer: Transformer.FailFast[F, Source, Dest]): F[Dest] =
    transformer.transform(value)

  inline def failFastVia[Func](inline function: Func)(using
    Func: FunctionMirror[Func]
  )(using Source: Mirror.ProductOf[Source]): F[Func.Return] =
    Transformations.failFastVia[F, Source, Func](function)(value)
}
