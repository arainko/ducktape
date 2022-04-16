package io.github.arainko.ducktape.internal.macros

import scala.quoted.*
import io.github.arainko.ducktape.FunctionMirror
import io.github.arainko.ducktape.internal.modules.*

private[ducktape] object FunctionMirrorMacros {
  transparent inline def create[F]: FunctionMirror[F] = ${ createMacro[F] }

  def createMacro[F: Type](using Quotes): Expr[FunctionMirror[F]] = {
    import quotes.reflect.*

    TypeRepr.of[F] match {
      case tpe @ AppliedType(_, tpeArgs) if tpe.isFunctionType =>
        val cons = TypeRepr.of[*:]
        val emptyTuple = TypeRepr.of[EmptyTuple]
        type IsTuple[T <: Tuple] = T

        val returnTpe = tpeArgs.last
        val argsTuple = tpeArgs.init.foldRight(emptyTuple)((curr, acc) => cons.appliedTo(curr :: acc :: Nil))

        (returnTpe.asType -> argsTuple.asType) match {
          case ('[ret], '[IsTuple[args]]) =>
            '{
              null.asInstanceOf[
                FunctionMirror[F] {
                  type Return = ret
                  type Args = args
                }
              ]
            }
        }

      case other => report.errorAndAbort(s"FunctionMirrors can only be created for functions. Got ${other.show} instead.")
    }
  }
}
