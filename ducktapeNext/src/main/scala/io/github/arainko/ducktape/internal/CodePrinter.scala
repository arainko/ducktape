package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.*

import scala.quoted.*

private[ducktape] object CodePrinter {
  inline def structure[A](inline value: A) = ${ structureMacro('value) }

  private def structureMacro[A: Type](value: Expr[A])(using Quotes): Expr[A] = {
    import quotes.reflect.*

    val struct = Printer.TreeStructure.show(value.asTerm)
    report.info(struct)
    value.asTerm.changeOwner(Symbol.spliceOwner).asExprOf[A]
  }

  inline def code[A](inline value: A): A = ${ codeMacro('value) }

  private def codeMacro[A: Type](value: Expr[A])(using Quotes): Expr[A] = {
    import quotes.reflect.*
    val struct = Printer.TreeShortCode.show(value.asTerm)
    report.info(struct)
    value
  }
}
