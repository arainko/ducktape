package io.github.arainko.ducktape.internal.modules

import scala.quoted.*

private[ducktape] type Pos = (quotes: Quotes) ?=> quotes.reflect.Position

private[ducktape] object Pos {
  def fromExpr(expr: Expr[Any])(using Quotes): quotes.reflect.Position = quotes.reflect.asTerm(expr).pos
}