package io.github.arainko.ducktape.internal

import scala.quoted.*

object Defaults {
  def of(struct: Structure.Product)(using Quotes): Map[String, Expr[Any]] = {
    import quotes.reflect.*

    Logger.debug(s"Searching for defaults for a type of", struct.tpe)

    val tpe = struct.tpe.repr
    val sym = tpe.typeSymbol
    val fieldNamesWithDefaults = 
      Logger.loggedDebug("Fields that have a default"):
        sym.caseFields.filter(_.flags.is(Flags.HasDefault)).map(_.name)
        
    val companionBody = sym.companionClass.tree.asInstanceOf[ClassDef].body // sus but it works?
    val companion = Ref(sym.companionModule)
    val defaultValues = companionBody.collect {
      case defaultMethod @ DefDef(name, _, _, _) if name.startsWith("$lessinit$greater$default") =>
        companion.select(defaultMethod.symbol).appliedToTypes(tpe.typeArgs).asExpr
    }

    fieldNamesWithDefaults.zip(defaultValues).toMap
  }
}
