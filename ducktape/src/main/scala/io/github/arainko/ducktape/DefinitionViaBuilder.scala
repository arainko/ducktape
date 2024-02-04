package io.github.arainko.ducktape

import io.github.arainko.ducktape.internal.TotalTransformations
import io.github.arainko.ducktape.internal.FallibleTransformations

final class DefinitionViaBuilder[Source, Dest, Func, Args <: FunctionArguments] private (function: Func) {
  inline def build(inline config: Field[Source, Args] | Case[Source, Args]*): Transformer[Source, Dest] =
    Transformer.Derived.FromFunction(value =>
      TotalTransformations.via[Source, Dest, Func, Args](value, function, "definition", config*)
    )

  def fallible[F[+x], M <: Mode[F]](using M): DefinitionViaBuilder.Fallible[F, M, Source, Dest, Func, Args] =
    DefinitionViaBuilder.Fallible[F, M, Source, Dest, Func, Args](function)
}

object DefinitionViaBuilder {
  private def instance[A, Func](function: Func): DefinitionViaBuilder[A, Nothing, Func, Nothing] =
    DefinitionViaBuilder[A, Nothing, Func, Nothing](function)

  def create[Source]: PartiallyApplied[Source] = ()

  opaque type PartiallyApplied[Source] = Unit

  object PartiallyApplied {
    extension [Source](partial: PartiallyApplied[Source]) {
      transparent inline def apply[Func](inline function: Func): Any = {
        val builder = instance[Source, Func](function)
        internal.Function.encodeAsType[[args <: FunctionArguments, retTpe] =>> DefinitionViaBuilder[Source, retTpe, Func, args]](
          function,
          builder
        )
      }
    }
  }

  final class Fallible[F[+x], M <: Mode[F], Source, Dest, Func, ArgSelector <: FunctionArguments] private[ducktape] (
    function: Func
  )(using F: M) {

    inline def build(
      inline config: Field.Fallible[F, Source, ArgSelector] | Case.Fallible[F, Source, ArgSelector]*
    ): Transformer.Fallible[F, Source, Dest] =
      Transformer.Fallible.Derived.FromFunction(value =>
        FallibleTransformations.via[F, Source, Dest, Func, ArgSelector](value, function, F, "definition", config*)
      )

  }
}
