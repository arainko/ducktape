package io.github.arainko.ducktape.internal.macros

import scala.quoted.*
import io.github.arainko.ducktape.internal.modules.*
import scala.deriving.Mirror as DerivingMirror

private[ducktape] class SelectorMacros(using val quotes: Quotes) extends Module, MirrorModule, FieldModule, SelectorModule

private[ducktape] object SelectorMacros {
  inline def selectedField[From, FieldType](inline selector: From => FieldType)(using
    From: DerivingMirror.ProductOf[From]
  ): String =
    ${ selectedFieldMacro[From, FieldType]('selector)(using 'From) }

  def selectedFieldMacro[From: Type, FieldType: Type](selector: Expr[From => FieldType])(using
    From: Expr[DerivingMirror.ProductOf[From]]
  )(using Quotes) =
    Expr(SelectorMacros().selectedField(selector))

  inline def caseOrdinal[From, Case <: From](using From: DerivingMirror.SumOf[From]) =
    ${ caseOrdinalMacro[From, Case](using 'From) }

  def caseOrdinalMacro[From: Type, Case <: From: Type](using From: Expr[DerivingMirror.SumOf[From]])(using Quotes) =
    Expr(SelectorMacros().caseOrdinal[From, Case])
}
