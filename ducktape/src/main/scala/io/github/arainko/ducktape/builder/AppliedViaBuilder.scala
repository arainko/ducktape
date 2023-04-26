package io.github.arainko.ducktape.builder

import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.fallible.Mode
import io.github.arainko.ducktape.fallible.Mode.{ Accumulating, FailFast }
import io.github.arainko.ducktape.function.*
import io.github.arainko.ducktape.internal.macros.*

import scala.compiletime.*
import scala.deriving.Mirror

final class AppliedViaBuilder[Source, Dest, Func, ArgSelector <: FunctionArguments] private (
  source: Source,
  function: Func
) {

  def fallible[F[+x], M <: Mode[F]](using M): AppliedViaBuilder.Fallible[F, M, Source, Dest, Func, ArgSelector] =
    AppliedViaBuilder.Fallible[F, M, Source, Dest, Func, ArgSelector](source, function)

  inline def transform(
    inline config: ArgBuilderConfig[Source, Dest, ArgSelector]*
  )(using Source: Mirror.ProductOf[Source]): Dest =
    Transformations.viaConfigured[Source, Dest, Func, ArgSelector](source, function, config*)

}

object AppliedViaBuilder {
  private def instance[Source, Dest, Func, ArgSelector <: FunctionArguments](
    source: Source,
    function: Func
  ) = AppliedViaBuilder[Source, Dest, Func, ArgSelector](source, function)

  transparent inline def create[Source, Func](source: Source, inline func: Func)(using Func: FunctionMirror[Func]): Any = {
    val builder = instance[Source, Func.Return, Func, Nothing](source, func)
    Functions.refineFunctionArguments(func, builder)
  }

  final class Fallible[F[+x], M <: Mode[F], Source, Dest, Func, ArgSelector <: FunctionArguments] private[ducktape] (
    source: Source,
    function: Func
  )(using F: M) {

    inline def transform(
      inline config: FallibleArgBuilderConfig[F, Source, Dest, ArgSelector] | ArgBuilderConfig[Source, Dest, ArgSelector]*
    )(using Mirror.ProductOf[Source]): F[Dest] =
      inline F match {
        case given Mode.Accumulating[F] =>
          Transformations.accumulatingViaConfigured[F, Source, Dest, Func, ArgSelector](source, function, config*)
        case given Mode.FailFast[F] =>
          Transformations.failFastViaConfigured[F, Source, Dest, Func, ArgSelector](source, function, config*)
        case other =>
          Errors.cannotDetermineTransformationMode
      }
  }
}
