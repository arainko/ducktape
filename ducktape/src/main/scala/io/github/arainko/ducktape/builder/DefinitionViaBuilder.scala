package io.github.arainko.ducktape.builder

import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.internal.macros.*
import scala.deriving.*
import io.github.arainko.ducktape.function.FunctionMirror

sealed abstract class DefinitionViaBuilder[Source, Dest, Func, NamedArguments <: Tuple](function: Func) {

  inline def build(
    inline config: ArgBuilderConfig[Source, Dest, NamedArguments]*
  )(using Mirror.ProductOf[Source]): Transformer[Source, Dest] = from =>
    ProductTransformerMacros.viaWithBuilder[Source, Dest, Func, NamedArguments](from, function, config*)
}

object DefinitionViaBuilder {
  def create[Source]: PartiallyApplied[Source] = () 

  opaque type PartiallyApplied[Source] = Unit

  object PartiallyApplied {
    extension [Source](partial: PartiallyApplied[Source]) {
      transparent inline def apply[Func](inline func: Func)(using Func: FunctionMirror[Func]) = {
        val builder = new DefinitionViaBuilder[Source, Func.Return, Func, Nothing](func) {}
        FunctionMacros.namedArguments(func, builder)
      }
    }
  }
}
