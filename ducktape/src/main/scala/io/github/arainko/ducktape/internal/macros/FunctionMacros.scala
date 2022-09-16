package io.github.arainko.ducktape.internal.macros

import io.github.arainko.ducktape.function.*
import io.github.arainko.ducktape.internal.modules.*

import scala.quoted.*

private[ducktape] class FunctionMacros(using val quotes: Quotes) extends Module, SelectorModule, FieldModule, MirrorModule {
  import FunctionMacros.*
  import quotes.reflect.*

  private val cons = TypeRepr.of[*:]
  private val emptyTuple = TypeRepr.of[EmptyTuple]
  private val namedArg = TypeRepr.of[NamedArgument]
  private val functionArguments = TypeRepr.of[FunctionArguments]

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

  def namedArguments[Func: Type, F[_ <: FunctionArguments[?]]: Type](function: Expr[Func], initial: Expr[F[Nothing]]) =
    function.asTerm match {
      case func @ FunctionLambda(valDefs, _) =>
        val args = valDefs.map(valdef => namedArg.appliedTo(ConstantType(StringConstant(valdef.name)) :: valdef.tpt.tpe :: Nil))
        val funcArgs = functionArguments.appliedTo(tupleify(args))
        val refinedFunctionArgs = refine(funcArgs, valDefs)
        refinedFunctionArgs.asType match {
          case '[IsFuncArgs[args]] => '{ $initial.asInstanceOf[F[args]] }
        }

      case other => report.errorAndAbort(s"Failed to extract named arguments from ${other.show}")
    }

  private def tupleify(tpes: List[TypeRepr]) =
    tpes.foldRight(emptyTuple)((curr, acc) => cons.appliedTo(curr :: acc :: Nil))

  private def refine(tpe: TypeRepr, valDefs: List[ValDef]) =
    valDefs.foldLeft(functionArguments)((tpe, valDef) => Refinement(tpe, valDef.name, valDef.tpt.tpe))

}

private[ducktape] object FunctionMacros {
  private type IsFuncArgs[A <: FunctionArguments[?]] = A

  transparent inline def createMirror[F]: FunctionMirror[F] = ${ createMirrorMacro[F] }

  def createMirrorMacro[Func: Type](using Quotes): Expr[FunctionMirror[Func]] =
    FunctionMacros().createMirror[Func]

  transparent inline def namedArguments[Func, F[_ <: FunctionArguments[?]]](
    inline function: Func,
    initial: F[Nothing]
  )(using FunctionMirror[Func]) = ${ namedArgumentsMacro[Func, F]('function, 'initial) }

  def namedArgumentsMacro[Func: Type, F[_ <: FunctionArguments[?]]: Type](
    function: Expr[Func],
    initial: Expr[F[Nothing]]
  )(using Quotes) = FunctionMacros().namedArguments[Func, F](function, initial)
}
