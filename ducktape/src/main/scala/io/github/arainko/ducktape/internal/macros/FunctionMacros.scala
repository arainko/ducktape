package io.github.arainko.ducktape.internal.macros

import scala.quoted.*
import io.github.arainko.ducktape.function.*
import io.github.arainko.ducktape.internal.modules.*

class FunctionMacros(using val quotes: Quotes) extends Module, SelectorModule, FieldModule, MirrorModule {
  import FunctionMacros.*
  import quotes.reflect.*

  def createMirror[Func: Type]: Expr[FunctionMirror[Func]] =
    TypeRepr.of[Func] match {
      case tpe @ AppliedType(_, tpeArgs) if tpe.isFunctionType =>
        val returnTpe = tpeArgs.last
        val args = tupleify(tpeArgs.init)

        (returnTpe.asType -> args.asType) match {
          case ('[ret], '[IsTuple[args]]) =>
            '{
              null.asInstanceOf[
                FunctionMirror[Func] {
                  type Return = ret
                  type Args = args
                }
              ]
            }
        }

      case other => report.errorAndAbort(s"FunctionMirrors can only be created for functions. Got ${other.show} instead.")
    }

  def namedArguments[Func: Type, F[_ <: Tuple]: Type](function: Expr[Func], initial: Expr[F[Nothing]]) =
    function.asTerm match {
      case func @ SelectorLambda(vals, body) =>
        val namedArg = TypeRepr.of[NamedArgument]
        val args = vals.map(valdef => namedArg.appliedTo(ConstantType(StringConstant(valdef.name)) :: valdef.tpt.tpe :: Nil))
        tupleify(args).asType match {
          case '[IsTuple[namedArgs]] => '{ $initial.asInstanceOf[F[namedArgs]] }
        }

      case other => report.errorAndAbort(s"Failed to extract named arguments from ${other.show}")
    }

  private def tupleify(tpes: List[TypeRepr]) = {
    val cons = TypeRepr.of[*:]
    val emptyTuple = TypeRepr.of[EmptyTuple]
    tpes.foldRight(emptyTuple)((curr, acc) => cons.appliedTo(curr :: acc :: Nil))
  }

}

private[ducktape] object FunctionMacros {
  private type IsTuple[A <: Tuple] = A

  transparent inline def createMirror[F]: FunctionMirror[F] = ${ createMirrorMacro[F] }

  def createMirrorMacro[Func: Type](using Quotes): Expr[FunctionMirror[Func]] =
    FunctionMacros().createMirror[Func]

  transparent inline def namedArguments[Func, F[_ <: Tuple]](
    inline function: Func,
    initial: F[Nothing]
  )(using FunctionMirror[Func]) = ${ namedArgumentsMacro[Func, F]('function, 'initial) }

  def namedArgumentsMacro[Func: Type, F[_ <: Tuple]: Type](function: Expr[Func], initial: Expr[F[Nothing]])(using Quotes) =
    FunctionMacros().namedArguments[Func, F](function, initial)
}
