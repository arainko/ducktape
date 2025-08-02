package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.internal.Structure.Product.Kind

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
  private[ducktape] def accessFieldByName(name: String, parentStructure: Structure.Product)(using Quotes) = {
    import quotes.reflect.*
    parentStructure.kind match
      case Kind.CaseClass =>
        expr.accessFieldByNameUnsafe(name)
      case Kind.NamedTuple(erasedTupleTpe) =>
        val fieldNames = parentStructure.fields.keys
        val idxOfField = parentStructure.fields.keys.indexOf(name)
        erasedTupleTpe match {
          case '[erasedTpe] =>
            if fieldNames.size < 23 then '{ $expr.asInstanceOf[erasedTpe] }.accessFieldByNameUnsafe(s"_${idxOfField + 1}")
            else
              val fieldTpe = parentStructure.fields(name).tpe
              (expr, fieldTpe) match {
                case '{ $prod } -> '[tpe] =>
                  '{ $prod.asInstanceOf[Product].productElement(${ Expr(idxOfField) }).asInstanceOf[tpe] }.asTerm
              }
        }

  }

  private[ducktape] def accessFieldByNameUnsafe(name: String)(using Quotes): quotes.reflect.Select = {
    import quotes.reflect.*
    Select.unique(expr.asTerm, name)
  }

  private[ducktape] def accesFieldByIndex(index: Int, parentStructure: Structure.Tuple)(using Quotes): Expr[Any] = {
    import quotes.reflect.*
    if parentStructure.isPlain then accessFieldByNameUnsafe(s"_${index + 1}").asExpr // tuple accessors are 1 based
    else
      val tpeAtIndex = parentStructure.elements(index).tpe
      (expr, tpeAtIndex) match {
        case '{ $prod: scala.Product } -> '[tpe] => '{ $prod.productElement(${ Expr(index) }).asInstanceOf[tpe] }
      }

  }
}

extension [A, B](self: Either[A, B]) {
  private[ducktape] inline def zipRight[AA >: A, C](inline that: Either[AA, C]): Either[AA, C] =
    self.flatMap(_ => that)
}

extension [A](self: A | None.type) {
  private[ducktape] inline def getOrElse[AA >: A](inline fallback: AA): AA =
    self.fold(fallback, a => a)

  private[ducktape] inline def fold[B](inline caseNone: B, inline caseA: A => B): B =
    self match
      case None => caseNone
      case a: A => caseA(a)

  // private[ducktape] inline def map[B](inline f: A => B): B | None.type = self.fold(None, f)

}

extension [A](self: Option[A]) {
  private[ducktape] def asUnion: A | None.type =
    self match
      case None        => None
      case Some(value) => value

}

private[ducktape] type None = None.type
