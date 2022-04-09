package io.github.arainko.ducktape.internal.modules

import scala.quoted.*
import io.github.arainko.ducktape.Transformer

private[internal] trait Module {
  val quotes: Quotes

  given Quotes = quotes

  import quotes.reflect.*

  object DerivingMirror {
    type SumOf[A] = Expr[deriving.Mirror.SumOf[A]]
    type ProductOf[A] = Expr[deriving.Mirror.ProductOf[A]]
    type Of[A] = Expr[deriving.Mirror.Of[A]]

    def of[A: Type]: Option[DerivingMirror.Of[A]] = Expr.summon[deriving.Mirror.Of[A]]
  }

  def derivedTransformer[A: Type, B: Type]: Option[Expr[Transformer[A, B]]] =
    DerivingMirror
      .of[A]
      .zip(DerivingMirror.of[B])
      .map {
        case ('{ $src: deriving.Mirror.Of[A] }, '{ $dest: deriving.Mirror.Of[B] }) =>
          '{ Transformer.derived[A, B](using $src, $dest) }
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
