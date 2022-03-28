package io.github.arainko.internal

import scala.quoted.*
import scala.deriving.*
import scala.quoted.Quotes

class Macro(using val quotes: Quotes) extends Module, FieldModule, MirrorModule, SelectorModule

object Macro {
  inline def call[A](using A: Mirror.Of[A]) = ${ callMacro[A]('A) }

  def callMacro[A](A: Expr[Mirror.Of[A]])(using Quotes) =
    Expr(Macro().Mirror(A).toList.flatMap(_.mirroredElemLabels))
}
