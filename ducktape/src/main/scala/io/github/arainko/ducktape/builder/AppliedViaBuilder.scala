package io.github.arainko.ducktape.builder

import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.internal.macros.*
import scala.deriving.Mirror
import io.github.arainko.ducktape.function.FunctionMirror
import io.github.arainko.ducktape.function.NamedArgument
import scala.compiletime.*

sealed abstract class AppliedViaBuilder[Source, Dest, Func, NamedArguments <: Tuple](source: Source, function: Func) {

  inline def apply(
    inline config: ArgConfig[Source, Dest, NamedArguments]*
  )(using Source: Mirror.ProductOf[Source]): Dest =
    ProductTransformerMacros.viaWithBuilder[Source, Dest, Func, NamedArguments](source, function, config*)
}

object AppliedViaBuilder {
  transparent inline def create[Source, Func](source: Source, inline func: Func)(using Func: FunctionMirror[Func]) = {
    val builder = new AppliedViaBuilder[Source, Func.Return, Func, Nothing](source, func) {}
    FunctionMacros.namedArguments(func, builder)
  }
}