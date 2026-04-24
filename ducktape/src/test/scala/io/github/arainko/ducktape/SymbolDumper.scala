package io.github.arainko.ducktape

object SymbolDumper {
  import scala.quoted.*

  def dumpSymbolInfo(using q: Quotes)(symbol: q.reflect.Symbol, ident: Int = 0): String = {
    import quotes.reflect.*
    val sb = new StringBuilder()
    val space = " " * ident

    def append(label: String, value: Any): Unit =
      sb.append(s"$space$label: ${value.toString}\n")

    def safeAppend(label: String)(thunk: => Any): Unit =
      try append(label, thunk)
      catch { case e: Throwable => append(label, s"THREW: ${e.getMessage}") }

    // Identity and Names
    append("name", symbol.name)
    append("fullName", symbol.fullName)
    append("exists", symbol.exists)
    append("isNoSymbol", symbol.isNoSymbol)

    // Ownership
    safeAppend("maybeOwner")(symbol.maybeOwner)
    safeAppend("owner")(symbol.owner)

    // Flags and Metadata
    append("flags", symbol.flags.show)
    append("privateWithin", symbol.privateWithin.map(_.show).getOrElse("None"))
    append("protectedWithin", symbol.protectedWithin.map(_.show).getOrElse("None"))
    append("isDefinedInCurrentRun", symbol.isDefinedInCurrentRun)
    append("pos", symbol.pos.map(_.toString).getOrElse("None"))
    append("docstring", symbol.docstring.getOrElse("None"))

    // Type-related (Experimental/Standard)
    // append("info", symbol.info.show)
    if symbol.isType then safeAppend("typeRef")(symbol.typeRef.show)
    if !symbol.isType then safeAppend("termRef")(symbol.termRef.show)

    // Structural Classifications
    append("isType", symbol.isType)
    append("isTerm", symbol.isTerm)
    append("isPackageDef", symbol.isPackageDef)
    append("isClassDef", symbol.isClassDef)
    append("isTypeDef", symbol.isTypeDef)
    append("isValDef", symbol.isValDef)
    append("isDefDef", symbol.isDefDef)
    append("isBind", symbol.isBind)
    append("isTypeParam", symbol.isTypeParam)
    append("isAbstractType", symbol.isAbstractType)
    append("isAnonymousClass", symbol.isAnonymousClass)
    append("isAnonymousFunction", symbol.isAnonymousFunction)
    append("isAliasType", symbol.isAliasType)
    append("isLocalDummy", symbol.isLocalDummy)
    append("isRefinementClass", symbol.isRefinementClass)

    // Trees and Methods
    append("signature", symbol.signature)
    append("paramSymss", symbol.paramSymss.map(_.map(_.name)))
    append("annotations", symbol.annotations.map(_.show))

    // Tree access is risky in macros, wrap in safety
    safeAppend("tree") {
      // Note: only works if -Yretain-trees is on or if it's in the current compilation unit
      symbol.tree.show
    }

    // Relations
    append("primaryConstructor", if symbol.primaryConstructor.exists then symbol.primaryConstructor.name else "None")
    if symbol.primaryConstructor.exists then {
      val ctor = symbol.primaryConstructor.termRef.widen

      ctor match {
        case MethodType(params, tpes, retTpe) =>
          append("params", params.mkString(", "))
          append("tpes", tpes.map(_.show))
      }

      append("primaryConstructor dump {\n", SymbolDumper.dumpSymbolInfo(symbol.primaryConstructor, ident + 2))
      append("primaryConstr termRef", symbol.primaryConstructor.termRef.widen.show(using Printer.TypeReprStructure)) // <-- return MethodType! migth use it to harvest the fields and shit

      symbol.primaryConstructor.paramSymss.foreach { symss =>
        symss.foreach { sym =>
          append(sym.name + " type", sym.termRef.widen.show)
          append(sym.name, SymbolDumper.dumpSymbolInfo(sym, ident + 4))
        }
      }
    }
    append("caseFields", symbol.caseFields.map(_.name))
    safeAppend("children")(symbol.children.map(_.name))
    append("companionClass", if symbol.companionClass.exists then symbol.companionClass.name else "None")
    append("companionModule", if symbol.companionModule.exists then symbol.companionModule.name else "None")
    append("moduleClass", if symbol.moduleClass.exists then symbol.moduleClass.name else "None")

    // Member lists (only viable for Classes/Traits)
    if symbol.isClassDef then {
      append("declarations", symbol.declarations.map(_.name))
      append("declaredFields", symbol.declaredFields.map(_.name))
      append("declaredMethods", symbol.declaredMethods.map(_.name))
      append("fieldMembers", symbol.fieldMembers.map(_.name))
      append("methodMembers", symbol.methodMembers.map(_.name))
    }

    sb.toString()
  }
}
