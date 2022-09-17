package io.github.arainko.ducktape.builder

import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.internal.macros.*
import scala.deriving.*
import io.github.arainko.ducktape.function.*

sealed abstract class DefinitionViaBuilder[Source, Dest, Func, ArgSelector <: FunctionArguments](function: Func) {

  inline def build(
    inline config: ArgBuilderConfig[Source, Dest, ArgSelector]*
  )(using Mirror.ProductOf[Source]): Transformer[Source, Dest] = from =>
    ProductTransformerMacros.viaConfigured[Source, Dest, Func, ArgSelector](from, function, config*)
}

object DefinitionViaBuilder {
  private[DefinitionViaBuilder] class Impl[Source, Dest, Func, ArgSelector <: FunctionArguments](
    function: Func
  ) extends DefinitionViaBuilder[Source, Dest, Func, ArgSelector](function)

  def create[Source]: PartiallyApplied[Source] = ()

  opaque type PartiallyApplied[Source] = Unit

  object PartiallyApplied {
    extension [Source](partial: PartiallyApplied[Source]) {
      transparent inline def apply[Func](inline func: Func)(using Func: FunctionMirror[Func]) = {
        // widen the type to not infer `DefinitionViaBuilder.Impl`, we're in a transparent inline method after all
        val builder: DefinitionViaBuilder[Source, Func.Return, Func, Nothing] = Impl(func)
        FunctionMacros.namedArguments(func, builder)
      }
    }
  }
}
