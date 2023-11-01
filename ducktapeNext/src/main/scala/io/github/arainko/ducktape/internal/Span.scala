package io.github.arainko.ducktape.internal

import scala.quoted.*

final case class Span(start: Int, end: Int) derives Debug {
  def toPosition(using Quotes): quotes.reflect.Position = {
    import quotes.reflect.*
    Position(SourceFile.current, start, end)
  }
}

object Span {
  def fromExpr(expr: Expr[Any])(using Quotes): Span = {
    import quotes.reflect.*
    fromPosition(expr.asTerm.pos)
  }

  def fromPosition(using Quotes)(pos: quotes.reflect.Position): Span = Span(pos.start, pos.end)

  def minimalAvailable(spans: List[Span])(using Quotes): Span = {
    import quotes.reflect.*

    val minSpan = spans.minByOption(_.start)
    val macroPos = Position.ofMacroExpansion
    // try to calculate a span that doesn't start at the same line a config span is at
    minSpan.map(min => Span(macroPos.start, min.start - macroPos.endColumn - 1)).getOrElse(Span.fromPosition(macroPos))
  }
}
