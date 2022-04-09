package io.github.arainko.ducktape.internal.macros

import scala.quoted.*

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
    val struct = Printer.TreeAnsiCode.show(value.asTerm)
    '{
      println(${ Expr(struct) })
      $value
    }
  }
}
