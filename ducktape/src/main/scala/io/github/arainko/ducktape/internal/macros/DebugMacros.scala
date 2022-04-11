package io.github.arainko.ducktape.internal.macros

import scala.quoted.*
import io.github.arainko.ducktape.*

object DebugMacros {
  inline def structure[A](inline value: A) = ${ structureMacro('value) }

  def structureMacro[A: Type](value: Expr[A])(using Quotes) = {
    import quotes.reflect.*
    val struct = Printer.TreeStructure.show(value.asTerm)
    '{
      println(${ Expr(struct) })
      $value
    }
  }

  inline def code[A](inline value: A) = ${ codeMacro('value) }

  def codeMacro[A: Type](value: Expr[A])(using Quotes) = {
    import quotes.reflect.*

    val struct = Printer.TreeShortCode.show(value.asTerm)
    '{
      println(${ Expr(struct) })
      $value
    }
  }

  transparent inline def methodParams(inline func: Any) = ${ methodParamsMacro('func) }

  def methodParamsMacro(function: Expr[Any])(using Quotes) = {
    import quotes.reflect.*

    println(function.asTerm.show(using Printer.TreeStructure))

    function.asTerm match {
      case b @ Inlined(_, _, l @ Lambda(vals, body)) =>
        vals.map(_.tpt.tpe)

        Select.unique(b, "apply").appliedTo(Literal(IntConstant(1))).asExpr
      // b.appliedTo(Literal(IntConstant(1))).asExpr
      // Expr(vals.map(v => v.name -> v.tpt.tpe.show).map(_.toString))
    }
  }

  transparent inline def functionMirror[A] = ${ functionMirrorMacro[A] }

  def functionMirrorMacro[A: Type](using Quotes) = {
    import quotes.reflect.*
    val tpe = TypeRepr.of[A]
    if (!tpe.isFunctionType) report.errorAndAbort("NOT A FUNCTION!")
    val cons = TypeRepr.of[*:]
    val AppliedType(_, tpeArgs) = tpe

    val returnTpe = tpeArgs.last
    val args = tpeArgs.init

    val argsTuple = args.foldRight(TypeRepr.of[EmptyTuple])((curr, acc) => cons.appliedTo(curr :: acc :: Nil))

    (returnTpe.asType -> argsTuple.asType) match {
      case ('[ret], '[args]) =>
        '{
          new FunctionMirror[A] {
            type Return = ret
            type Args = args
          }
        }
    }
  }
}
