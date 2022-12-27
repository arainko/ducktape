package io.github.arainko.ducktape.builder

import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.function.*
import io.github.arainko.ducktape.internal.macros.*

import scala.deriving.*

final class DefinitionViaBuilder[Source, Dest, Func, ArgSelector <: FunctionArguments] private (function: Func) {

  inline def build(
    inline config: ArgBuilderConfig[Source, Dest, ArgSelector]*
  )(using Mirror.ProductOf[Source]): Transformer[Source, Dest] =
    from => Transformations.viaConfigured[Source, Dest, Func, ArgSelector](from, function, config*)

}

object DefinitionViaBuilder {
  private def instance[Source, Dest, Func, ArgSelector <: FunctionArguments](function: Func) =
    DefinitionViaBuilder[Source, Dest, Func, ArgSelector](function)

  def create[Source]: PartiallyApplied[Source] = ()

  opaque type PartiallyApplied[Source] = Unit

  object PartiallyApplied {
    extension [Source](partial: PartiallyApplied[Source]) {
      transparent inline def apply[Func](inline func: Func)(using Func: FunctionMirror[Func]): Any = {
        val builder = instance[Source, Func.Return, Func, Nothing](func)
        Functions.refineFunctionArguments(func, builder)
      }
    }
  }

  final class FailFast[F[+x], Source, Dest, Func, ArgSelector <: FunctionArguments] private[ducktape] (function: Func) {
    inline def build(
      inline config: FallibleArgBuilderConfig[F, Source, Dest, ArgSelector] | ArgBuilderConfig[Source, Dest, ArgSelector]*
    )(using
      F: Transformer.FailFast.Support[F],
      Source: Mirror.ProductOf[Source]
    ): Transformer.FailFast[F, Source, Dest] =
      new {
        def transform(value: Source): F[Dest] =
          Transformations.failFastViaConfigured[F, Source, Dest, Func, ArgSelector](value, function, config*)
      }
  }

  final class Accumulating[F[+x], Source, Dest, Func, ArgSelector <: FunctionArguments] private[ducktape] (function: Func) {
    inline def build(
      inline config: FallibleArgBuilderConfig[F, Source, Dest, ArgSelector] | ArgBuilderConfig[Source, Dest, ArgSelector]*
    )(using
      F: Transformer.Accumulating.Support[F],
      Source: Mirror.ProductOf[Source]
    ): Transformer.Accumulating[F, Source, Dest] =
      new {
        def transform(value: Source): F[Dest] =
          Transformations.accumulatingViaConfigured[F, Source, Dest, Func, ArgSelector](value, function, config*)
      }
  }
}
