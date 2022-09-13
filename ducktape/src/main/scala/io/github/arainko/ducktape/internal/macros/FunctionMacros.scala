package io.github.arainko.ducktape.internal.macros

import io.github.arainko.ducktape.function.*
import io.github.arainko.ducktape.internal.modules.*

import scala.quoted.*

class FunctionMacros(using val quotes: Quotes) extends Module, SelectorModule, FieldModule, MirrorModule {
  import FunctionMacros.*
  import quotes.reflect.*

  def createMirror[Func: Type]: Expr[FunctionMirror[Func]] =
    TypeRepr.of[Func] match {
      case tpe @ AppliedType(_, tpeArgs) if tpe.isFunctionType =>
        val returnTpe = tpeArgs.last

        returnTpe.asType match {
          case '[ret] =>
            '{
              FunctionMirror.asInstanceOf[
                FunctionMirror[Func] {
                  type Return = ret
                }
              ]
            }
        }

      case other => report.errorAndAbort(s"FunctionMirrors can only be created for functions. Got ${other.show} instead.")
    }

  def namedArguments[Func: Type, F[_ <: Tuple, _ <: FunctionArguments[?]]: Type](function: Expr[Func], initial: Expr[F[Nothing, Nothing]]) =
    function.asTerm match {
      case func @ FunctionLambda(vals, body) =>
        val namedArg = TypeRepr.of[NamedArgument]
        val wrapper = TypeRepr.of[F]
        val args = vals.map(valdef => namedArg.appliedTo(ConstantType(StringConstant(valdef.name)) :: valdef.tpt.tpe :: Nil))
        val argTuple = tupleify(args)
        val functionArguments = TypeRepr.of[FunctionArguments].appliedTo(argTuple)
        val refinedFunctionArgs = 
          vals.foldLeft(functionArguments)((tpe, valDef) => Refinement(tpe, valDef.name, valDef.tpt.tpe))
        // val appliedArgs = argT
        tupleify(args).asType -> refinedFunctionArgs.asType match {
          case ('[IsTuple[namedArgs]], '[IsFuncArgs[args]]) => '{ $initial.asInstanceOf[F[namedArgs, args]] }
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
  private type IsFuncArgs[A <: FunctionArguments[?]] = A

  transparent inline def createMirror[F]: FunctionMirror[F] = ${ createMirrorMacro[F] }

  def createMirrorMacro[Func: Type](using Quotes): Expr[FunctionMirror[Func]] =
    FunctionMacros().createMirror[Func]

  transparent inline def namedArguments[Func, F[_ <: Tuple, _ <: FunctionArguments[?]]](
    inline function: Func,
    initial: F[Nothing, Nothing]
  )(using FunctionMirror[Func]) = ${ namedArgumentsMacro[Func, F]('function, 'initial) }

  def namedArgumentsMacro[Func: Type, F[_ <: Tuple, _ <: FunctionArguments[?]]: Type](function: Expr[Func], initial: Expr[F[Nothing, Nothing]])(using Quotes) =
    FunctionMacros().namedArguments[Func, F](function, initial)
}
