package io.github.arainko.ducktape.internal.macros

import io.github.arainko.ducktape.function.*
import io.github.arainko.ducktape.internal.macros.FunctionMacros

private[ducktape] object Functions {
  transparent inline def deriveMirror[Func]: FunctionMirror[Func] = ${ FunctionMacros.deriveMirror[Func] }

  transparent inline def refineFunctionArguments[Func, F[x <: FunctionArguments]](
    inline function: Func,
    initial: F[Nothing]
  )(using FunctionMirror[Func]): Any = ${ FunctionMacros.refineFunctionArguments[Func, F]('function, 'initial) }
}
