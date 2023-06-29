package io.github.arainko.ducktape.internal.modules

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

  private[ducktape] def accessField(field: Field)(using Quotes): quotes.reflect.Select =
    accessFieldByName(field.name)
}
