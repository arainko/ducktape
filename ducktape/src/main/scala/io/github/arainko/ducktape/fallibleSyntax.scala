package io.github.arainko.ducktape

import io.github.arainko.ducktape.fallible.FallibleTransformer
import io.github.arainko.ducktape.function.*
import io.github.arainko.ducktape.internal.macros.{ Errors, Transformations }

import scala.deriving.Mirror

extension [F[+x], Source](value: Source)(using F: Transformer.Mode[F]) {

  inline def fallibleTo[Dest](using transformer: FallibleTransformer[F, Source, Dest]): F[Dest] =
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
