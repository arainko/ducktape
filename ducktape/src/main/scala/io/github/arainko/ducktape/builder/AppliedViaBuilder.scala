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

  inline def transform(
    inline config: ArgBuilderConfig[Source, Dest, ArgSelector]*
  )(using Source: Mirror.ProductOf[Source]): Dest =
    NormalizationMacros.normalize(ProductTransformerMacros.viaConfigured[Source, Dest, Func, ArgSelector](source, function, config*))
}

object AppliedViaBuilder {
  private def instance[Source, Dest, Func, ArgSelector <: FunctionArguments](
    source: Source,
    function: Func
  ) = AppliedViaBuilder[Source, Dest, Func, ArgSelector](source, function)

  transparent inline def create[Source, Func](source: Source, inline func: Func)(using Func: FunctionMirror[Func]) = {
    val builder = instance[Source, Func.Return, Func, Nothing](source, func)
    FunctionMacros.namedArguments(func, instance[Source, Func.Return, Func, Nothing](source, func))
  }
}
