package io.github.arainko.ducktape

import io.github.arainko.ducktape.Builder.Applied
import io.github.arainko.ducktape.internal.macros.*
import scala.runtime.FunctionXXL
import javax.swing.DebugGraphics
import scala.deriving.Mirror

extension [From](value: From) {
  def into[To]: Applied[From, To, EmptyTuple] = Builder.applied[From, To](value)

  def to[To](using Transformer[From, To]): To = Transformer[From, To].transform(value)

  /*
    TODO: This should NOT be `transparent` by using a path dependent type of FunctionMirror#Return
    but the compiler (sometimes) crashes with a `StaleSymbol` error when doing this.
    A workaround to this is binding another type variable `B` to FunctionMirror.Aux[Func, ?, B]
    but that requires us to introduce another type variable and can mislead users (despite it being inferred)
   */
  transparent inline def via[Func](inline function: Func)(using
    Func: FunctionMirror[Func],
    A: Mirror.ProductOf[From]
  ) = ProductTransformerMacros.via(value, function)
}
