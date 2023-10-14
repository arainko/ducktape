package io.github.arainko.tooling

import scala.quoted.*

private[arainko] opaque type FullName <: String = String

private[arainko] object FullName {

  inline given derived(using DummyImplicit): FullName = ${ ownerMacro }

  private def ownerMacro(using Quotes) = {
    import quotes.reflect.*

    val pos = Position.ofMacroExpansion

    val sourceFile = s"${pos.sourceFile.name}:${pos.startLine + 1}"

    val rendered =
      List
        .unfold(Symbol.spliceOwner)(sym => Option.when(!sym.isNoSymbol)(sym -> sym.maybeOwner))
        .collect {
          case sym if !sym.flags.is(Flags.Synthetic) && !sym.flags.is(Flags.Package) && !sym.isLocalDummy => sym.name
        }
        .reverse
        .mkString(".")
        .replace("$", "")

    '{ ${ Expr(s"$rendered @ $sourceFile") }: FullName }
  }
}
