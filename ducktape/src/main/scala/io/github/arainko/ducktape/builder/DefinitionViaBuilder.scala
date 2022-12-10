package io.github.arainko.ducktape.builder

import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.function.*
import io.github.arainko.ducktape.internal.macros.*

import scala.deriving.*

final class DefinitionViaBuilder[Source, Dest, Func, ArgSelector <: FunctionArguments] private (function: Func) {

  inline def build(
    inline config: ArgBuilderConfig[Source, Dest, ArgSelector]*
  )(using Mirror.ProductOf[Source]): Transformer[Source, Dest] =
    from => ProductTransformerMacros.viaConfigured[Source, Dest, Func, ArgSelector](from, function, config*)

}

object DefinitionViaBuilder {
  private def instance[Source, Dest, Func, ArgSelector <: FunctionArguments](function: Func) =
    DefinitionViaBuilder[Source, Dest, Func, ArgSelector](function)

  def create[Source]: PartiallyApplied[Source] = ()

  opaque type PartiallyApplied[Source] = Unit

  object PartiallyApplied {
    extension [Source](partial: PartiallyApplied[Source]) {
      transparent inline def apply[Func](inline func: Func)(using Func: FunctionMirror[Func]) = {
        val builder = instance[Source, Func.Return, Func, Nothing](func)
        FunctionMacros.namedArguments(func, builder)
      }
    }
  }
}
