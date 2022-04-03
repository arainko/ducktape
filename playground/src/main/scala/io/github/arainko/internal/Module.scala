package io.github.arainko.internal

import scala.quoted.*


trait Module {
  val quotes: Quotes

  given Quotes = quotes

  import quotes.reflect.*

  object DerivingMirror {
    type SumOf[A] = Expr[deriving.Mirror.SumOf[A]]
    type ProductOf[A] = Expr[deriving.Mirror.ProductOf[A]]
  }

  def Constructor(tpe: TypeRepr): Term = {
    val (repr, constructor, tpeArgs) = tpe match {
      case AppliedType(repr, reprArguments) => (repr, repr.typeSymbol.primaryConstructor, reprArguments)
      case notApplied                       => (tpe, tpe.typeSymbol.primaryConstructor, Nil)
    }

    New(Inferred(repr))
      .select(constructor)
      .appliedToTypes(tpeArgs)
  }
}
