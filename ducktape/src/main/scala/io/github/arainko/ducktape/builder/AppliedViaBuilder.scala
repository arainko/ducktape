package io.github.arainko.ducktape.builder

import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.function.*
import io.github.arainko.ducktape.internal.macros.*

import scala.compiletime.*
import scala.deriving.Mirror

final class AppliedViaBuilder[Source, Dest, Func, ArgSelector <: FunctionArguments] private (
  source: Source,
  function: Func
) {

  def failFast[F[+x]]: AppliedViaBuilder.FailFast[F, Source, Dest, Func, ArgSelector] =
    AppliedViaBuilder.FailFast[F, Source, Dest, Func, ArgSelector](source, function)

  def accumulating[F[+x]]: AppliedViaBuilder.Accumulating[F, Source, Dest, Func, ArgSelector] =
    AppliedViaBuilder.Accumulating[F, Source, Dest, Func, ArgSelector](source, function)

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

  final class FailFast[F[+x], Source, Dest, Func, ArgSelector <: FunctionArguments] private[AppliedViaBuilder] (
    source: Source,
    function: Func
  ) {
    inline def transform(
      inline config: FallibleArgBuilderConfig[F, Source, Dest, ArgSelector] | ArgBuilderConfig[Source, Dest, ArgSelector]*
    )(using F: Transformer.FailFast.Support[F], Source: Mirror.ProductOf[Source]): F[Dest] =
      Transformations.failFastViaConfigured[F, Source, Dest, Func, ArgSelector](source, function, config*)
  }

  final class Accumulating[F[+x], Source, Dest, Func, ArgSelector <: FunctionArguments] private[AppliedViaBuilder] (
    source: Source,
    function: Func
  ) {
    inline def transform(
      inline config: FallibleArgBuilderConfig[F, Source, Dest, ArgSelector] | ArgBuilderConfig[Source, Dest, ArgSelector]*
    )(using F: Transformer.Accumulating.Support[F], Source: Mirror.ProductOf[Source]): F[Dest] =
      Transformations.accumulatingViaConfigured[F, Source, Dest, Func, ArgSelector](source, function, config*)
  }
}
