package io.github.arainko.ducktape.macros

import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.internal.modules.MakeTransformer

import scala.quoted.*
object MakeTransformerChecker {
  inline def check(inline transformer: Transformer[?, ?]): Unit = ${ checkMacro('transformer) }

  def checkMacro(transformer: Expr[Transformer[?, ?]])(using Quotes): Expr[Unit] = {
    import quotes.reflect.*

    MakeTransformer
      .unapply(transformer.asTerm)
      .map(_ => '{ () })
      .getOrElse(report.errorAndAbort(s"Not matched: ${transformer.asTerm.show(using Printer.TreeStructure)}"))
  }
}
