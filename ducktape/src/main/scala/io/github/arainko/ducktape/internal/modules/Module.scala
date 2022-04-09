package io.github.arainko.ducktape.internal.modules

import scala.quoted.*
import io.github.arainko.ducktape.Transformer

private[internal] trait Module {
  val quotes: Quotes

  given Quotes = quotes

  import quotes.reflect.*

  given Printer[TypeRepr] = Printer.TypeReprShortCode
  given Printer[Tree] = Printer.TreeShortCode

  object DerivingMirror {
    type SumOf[A] = Expr[deriving.Mirror.SumOf[A]]
    type ProductOf[A] = Expr[deriving.Mirror.ProductOf[A]]
    type Of[A] = Expr[deriving.Mirror.Of[A]]

    def of[A: Type]: Option[DerivingMirror.Of[A]] = Expr.summon[deriving.Mirror.Of[A]]
  }
}
