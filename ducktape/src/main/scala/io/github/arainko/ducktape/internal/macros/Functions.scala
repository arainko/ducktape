package io.github.arainko.ducktape.internal.macros

import io.github.arainko.ducktape.function.*
import io.github.arainko.ducktape.internal.modules.FunctionMacros

private[ducktape] object Functions {
  transparent inline def deriveMirror[Func]: FunctionMirror[Func] = ${ FunctionMacros.createMirror[Func] }

  transparent inline def refineFunctionArguments[Func, F[x <: FunctionArguments]](
    inline function: Func,
    initial: F[Nothing]
  )(using FunctionMirror[Func]): Any = ${ FunctionMacros.namedArguments[Func, F]('function, 'initial) }
}
