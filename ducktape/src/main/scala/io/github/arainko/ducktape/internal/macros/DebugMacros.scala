package io.github.arainko.ducktape.internal.macros

import io.github.arainko.ducktape.*

import scala.quoted.*

private[ducktape] object DebugMacros {
  inline def structure[A](inline value: A) = ${ structureMacro('value) }

  def structureMacro[A: Type](value: Expr[A])(using Quotes) = {
    import quotes.reflect.*
    val struct = Printer.TreeStructure.show(value.asTerm)
    report.info(struct)
    value
  }

  inline def code[A](inline value: A) = ${ codeCompiletimeMacro('value) }

  def codeCompiletimeMacro[A: Type](value: Expr[A])(using Quotes) = {
    import quotes.reflect.*
    val struct = Printer.TreeShortCode.show(value.asTerm)
    report.info(struct)
    value
  }
}
