package io.github.arainko.ducktape

import io.github.arainko.ducktape.builder.*
import io.github.arainko.ducktape.function.*
import io.github.arainko.ducktape.internal.macros.*

import scala.deriving.Mirror

extension [Source](value: Source) {
  def into[Dest]: AppliedBuilder[Source, Dest] = AppliedBuilder(value)

  def to[Dest](using Transformer[Source, Dest]): Dest = Transformer[Source, Dest].transform(value)

  transparent inline def intoVia[Func](inline function: Func)(using Mirror.ProductOf[Source], FunctionMirror[Func]) =
    AppliedViaBuilder.create(value, function)

  inline def via[Func](inline function: Func)(using
    Func: FunctionMirror[Func],
    Source: Mirror.ProductOf[Source]
  ): Func.Return = Transformations.via(value, function)
}

extension [F[+x], Source](value: Source)(using F: Transformer.Mode[F]) {

  inline def fallibleTo[Dest](using transformer: Transformer.Fallible[F, Source, Dest]): F[Dest] =
    transformer.transform(value)

  inline def fallibleVia[Func](inline function: Func)(using
    Func: FunctionMirror[Func]
  )(using Source: Mirror.ProductOf[Source]): F[Func.Return] =
    inline F match {
      case given fallible.Mode.FailFast[F] =>
        Transformations.failFastVia[F, Source, Func](function)(value)
      case given fallible.Mode.Accumulating[F] =>
        Transformations.accumulatingVia[F, Source, Func](function)(value)
      case other =>
        Errors.cannotDetermineTransformationMode
    }

}
