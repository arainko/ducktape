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

  inline def matchTest[A](inline expr: A) = ${ matchTestMacro('expr) }

  def matchTestMacro[A](expr: Expr[A])(using Quotes) = {
    import quotes.reflect.*

    object StripNoisyNodes extends TreeMap {
      override def transformTerm(tree: Term)(owner: Symbol): Term =
        tree match {
          case Inlined(_, Nil, term) => transformTerm(term)(owner)
          case other                 => super.transformTerm(other)(owner)
        }
    }

    val stripped = StripNoisyNodes.transformTerm(expr.asTerm)(Symbol.spliceOwner)

    stripped match {
      case Block(_, Typed(Apply(_, Lambda(param :: Nil, body) :: Nil), _)) =>
        // report.info(param.show)
        report.info(body.show(using Printer.TreeStructure))
        stripped.asExpr
    }
  }
}
