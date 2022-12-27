package io.github.arainko.ducktape

import io.github.arainko.ducktape.builder.*
import io.github.arainko.ducktape.function.*
import io.github.arainko.ducktape.internal.macros.*
import io.github.arainko.ducktape.internal.modules.*

import scala.annotation.experimental
import scala.deriving.Mirror

extension [Source](value: Source) {
  def into[Dest]: AppliedBuilder[Source, Dest] = AppliedBuilder(value)

  def to[Dest](using Transformer[Source, Dest]): Dest = Transformer[Source, Dest].transform(value)

  def failFastTo[F[+x], Dest](using failFast: Transformer.FailFast[F, Source, Dest]): F[Dest] =
    failFast.transform(value)

  def accumulatingTo[F[+x], Dest](using accumulating: Transformer.Accumulating[F, Source, Dest]): F[Dest] =
    accumulating.transform(value)

  transparent inline def intoVia[Func](inline function: Func)(using Mirror.ProductOf[Source], FunctionMirror[Func]) =
    AppliedViaBuilder.create(value, function)

  inline def via[Func](inline function: Func)(using
    Func: FunctionMirror[Func],
    Source: Mirror.ProductOf[Source]
  ): Func.Return = Transformations.via(value, function)

  def failFastVia[F[+x]]: FailFastViaPartiallyApplied[F, Source] =
    FailFastViaPartiallyApplied[F, Source](value)

  def accumulatingVia[F[+x]]: AccumulatingViaPartiallyApplied[F, Source] =
    AccumulatingViaPartiallyApplied[F, Source](value)
}

final class AccumulatingViaPartiallyApplied[F[+x], Source] private[ducktape] (private val source: Source) {
  inline def apply[Func](inline function: Func)(using
    Func: FunctionMirror[Func]
  )(using Source: Mirror.ProductOf[Source], F: Transformer.Accumulating.Support[F]): F[Func.Return] =
    Transformations.accumulatingVia[F, Source, Func](function)(source)
}

final class FailFastViaPartiallyApplied[F[+x], Source] private[ducktape] (private val source: Source) {
  inline def apply[Func](inline function: Func)(using
    Func: FunctionMirror[Func]
  )(using Source: Mirror.ProductOf[Source], F: Transformer.FailFast.Support[F]): F[Func.Return] =
    Transformations.failFastVia[F, Source, Func](function)(source)
}
