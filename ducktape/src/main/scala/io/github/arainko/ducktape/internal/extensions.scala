package io.github.arainko.ducktape.internal

import scala.quoted.*

extension (tpe: Type[? <: AnyKind]) {
  private[ducktape] def fullName(using Quotes): String = {
    import quotes.reflect.*

    TypeRepr.of(using tpe).show(using Printer.TypeReprCode)
  }

  private[ducktape] def repr(using Quotes): quotes.reflect.TypeRepr =
    quotes.reflect.TypeRepr.of(using tpe)
}

extension (expr: Expr[Any]) {
  private[ducktape] def accessFieldByName(name: String)(using Quotes): quotes.reflect.Select = {
    import quotes.reflect.*
    Select.unique(expr.asTerm, name)
  }

  private[ducktape] def accesFieldByIndex(index: Int, parentStructure: Structure.Tuple)(using Quotes): Expr[Any] = {
    import quotes.reflect.*
    if parentStructure.isPlain then accessFieldByName(s"_${index + 1}").asExpr // tuple accessors are 1 based
    else
      val tpeAtIndex = parentStructure.elements(index).tpe
      (expr, tpeAtIndex) match {
        case '{ $prod: scala.Product } -> '[tpe] => '{ $prod.productElement(${ Expr(index) }).asInstanceOf[tpe] }
      }

  }
}
