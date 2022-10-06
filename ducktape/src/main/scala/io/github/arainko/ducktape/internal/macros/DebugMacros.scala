package io.github.arainko.ducktape.internal.macros

import io.github.arainko.ducktape.*

import scala.quoted.*

object DebugMacros {
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

  // inline def matchTest[A](inline expr: A) = ${ matchTestMacro('expr) }

  // def matchTestMacro[A](expr: Expr[A])(using Quotes) = {
  //   import quotes.reflect.*

  //   expr match {
  //     case '{ Transformer.ForProduct.make[a, b]($lambda) } =>
  //       report.info(expr.asTerm.show)
  //       expr
  //     case other =>
  //       report.info(s"OTHER: ${other.asTerm.show}")
  //       expr
  //   }
  // }
}
