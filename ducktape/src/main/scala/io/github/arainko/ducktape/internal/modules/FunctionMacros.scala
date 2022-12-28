package io.github.arainko.ducktape.internal.modules

import io.github.arainko.ducktape.function.*
import io.github.arainko.ducktape.internal.modules.*

import scala.quoted.*

private[ducktape] object FunctionMacros {

  def createMirror[Func: Type](using Quotes): Expr[FunctionMirror[Func]] = {
    import quotes.reflect.*

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
  }
  
  def namedArguments[Func: Type, F[x <: FunctionArguments]: Type](
    function: Expr[Func],
    initial: Expr[F[Nothing]]
  )(using Quotes) = {
    import quotes.reflect.*

    function.asTerm match {
      case func @ Selectors.FunctionLambda(valDefs, _) =>
        refine(TypeRepr.of[FunctionArguments], valDefs).asType match {
          case '[IsFuncArgs[args]] => '{ $initial.asInstanceOf[F[args]] }
        }

      case other => report.errorAndAbort(s"Failed to extract named arguments from ${other.show}")
    }
  }

  private def refine(using Quotes)(tpe: quotes.reflect.TypeRepr, valDefs: List[quotes.reflect.ValDef]) = {
    import quotes.reflect.*

    valDefs.foldLeft(TypeRepr.of[FunctionArguments])((tpe, valDef) => Refinement(tpe, valDef.name, valDef.tpt.tpe))
  }

  private type IsFuncArgs[A <: FunctionArguments] = A
}

// private[ducktape] object FunctionMacros {
//   private type IsFuncArgs[A <: FunctionArguments] = A

//   transparent inline def createMirror[F]: FunctionMirror[F] = ${ createMirrorMacro[F] }

//   def createMirrorMacro[Func: Type](using Quotes): Expr[FunctionMirror[Func]] =
//     FunctionMacros().createMirror[Func]

//   transparent inline def namedArguments[Func, F[_ <: FunctionArguments]](
//     inline function: Func,
//     initial: F[Nothing]
//   )(using FunctionMirror[Func]) = ${ namedArgumentsMacro[Func, F]('function, 'initial) }

//   def namedArgumentsMacro[Func: Type, F[_ <: FunctionArguments]: Type](
//     function: Expr[Func],
//     initial: Expr[F[Nothing]]
//   )(using Quotes) = FunctionMacros().namedArguments[Func, F](function, initial)
// }
