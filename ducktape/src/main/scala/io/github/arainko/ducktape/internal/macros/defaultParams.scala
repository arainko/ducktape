package io.github.arainko.ducktape.internal.macros

import scala.quoted.*

inline def defaultParams[T]: Map[String, Any] = ${ defaultParamsImpl[T] }

private def defaultParamsImpl[T](using quotes: Quotes, tpe: Type[T]): Expr[Map[String, Any]] = {
  import quotes.reflect.*

  val typ = TypeRepr.of[T]
  val sym = typ.typeSymbol
  val typeArgs = typ.typeArgs
  val mod = Ref(sym.companionModule)
  val names = sym.caseFields.filter(_.flags.is(Flags.HasDefault)).map(_.name)
  val namesExpr: Expr[List[String]] =
    Expr.ofList(names.map(Expr(_)))

  val body = sym.companionClass.tree.asInstanceOf[ClassDef].body
  val idents: List[Term] = body.collect {
    case deff @ DefDef(name, _, _, _) if name.startsWith("$lessinit$greater$default") =>
      mod.select(deff.symbol).appliedToTypes(typeArgs)
  }
  val identsExpr: Expr[List[Any]] =
    Expr.ofList(idents.map(_.asExpr))

  '{ $namesExpr.zip($identsExpr).toMap }
}
