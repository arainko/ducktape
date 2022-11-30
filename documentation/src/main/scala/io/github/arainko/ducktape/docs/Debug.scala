package io.github.arainko.ducktape.docs

import scala.quoted.*

object Debug {
  inline def printCode[A](inline value: A): A = ${ codeCompiletimeMacro('value) }

  def codeCompiletimeMacro[A: Type](value: Expr[A])(using Quotes): Expr[A] = {
    import quotes.reflect.*
    val struct = Printer.TreeShortCode.show(value.asTerm)
    report.info(struct)
    '{
      println(${ Expr(struct) })
      $value
    }
  }
}
