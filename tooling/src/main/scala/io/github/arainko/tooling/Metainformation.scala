package io.github.arainko.ducktape.internal

import scala.quoted.*

private[ducktape] opaque type Metainformation <: String = String

private[ducktape] object Metainformation {

  inline given derived(using DummyImplicit): Metainformation = ${ ownerMacro }

  private def ownerMacro(using Quotes) = {
    import quotes.reflect.*

    val pos = Position.ofMacroExpansion

    val sourceFile = s"${pos.sourceFile.name}:${pos.startLine + 1}"

    val closestOwner =
      List
        .unfold(Symbol.spliceOwner)(sym => Option.when(!sym.isNoSymbol)(sym -> sym.maybeOwner))
        .collect {
          case sym if !sym.flags.is(Flags.Synthetic) && !sym.flags.is(Flags.Package) && !sym.isLocalDummy => sym.name
        }
        .reverse
        .mkString(".")
        .replace("$", "")

    '{ ${ Expr(s"$closestOwner @ $sourceFile") }: Metainformation }
  }
}
