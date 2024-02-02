package io.github.arainko.ducktape

import io.github.arainko.ducktape.internal.TotalTransformations
import io.github.arainko.ducktape.internal.FallibleTransformations

final class AppliedViaBuilder[Source, Dest, Func, Args <: FunctionArguments] private (value: Source, function: Func) {
  inline def transform(inline config: Field[Source, Args] | Case[Source, Args]*): Dest =
    TotalTransformations.via[Source, Dest, Func, Args](value, function, "transformation", config*)

  def fallible[F[+x], M <: Mode[F]](using M): AppliedViaBuilder.Fallible[F, M, Source, Dest, Func, Args] =
    AppliedViaBuilder.Fallible[F, M, Source, Dest, Func, Args](value, function)
}

object AppliedViaBuilder {
  private def instance[A, Func](source: A, function: Func): AppliedViaBuilder[A, Nothing, Func, Nothing] =
    AppliedViaBuilder[A, Nothing, Func, Nothing](source, function)

  transparent inline def create[A, Func](source: A, inline function: Func): Any = {
    val inst = instance(source, function)
    internal.Function
      .encodeAsType[[args <: FunctionArguments, retTpe] =>> AppliedViaBuilder[A, retTpe, Func, args]](function, inst)
  }

  final class Fallible[F[+x], M <: Mode[F], Source, Dest, Func, Args <: FunctionArguments] private[ducktape] (
    source: Source,
    function: Func
  )(using F: M) {

    inline def transform(
      inline config: Field.Fallible[F, Source, Args] | Case.Fallible[F, Source, Args]*
    ): F[Dest] = FallibleTransformations.via[F, Source, Dest, Func, Args](source, function, F, "transformation", config*)
      
  }
}
