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

  
}
