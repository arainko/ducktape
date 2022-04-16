package io.github.arainko.ducktape

import io.github.arainko.ducktape.Builder.Applied
import io.github.arainko.ducktape.internal.macros.*
import scala.runtime.FunctionXXL
import javax.swing.DebugGraphics
import scala.deriving.Mirror

extension [From](value: From) {
  def into[To]: Applied[From, To, EmptyTuple] = Builder.applied[From, To](value)

  def to[To](using Transformer[From, To]): To = Transformer[From, To].transform(value)

  //TODO: Figure out stale symbol compiler crash when 
  // using F.Return as return type instead of binding a variable to the return type
  inline def via[Func, To](inline function: Func)(using Func: FunctionMirror.Aux[Func, ?, To], A: Mirror.ProductOf[From]): To =
    ProductTransformerMacros.via(value, function)
}
