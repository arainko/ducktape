package io.github.arainko.ducktape.internal

import scala.quoted.*

private[ducktape] final case class Span(start: Int, end: Int) derives Debug {
  def toPosition(using Quotes): quotes.reflect.Position = {
    import quotes.reflect.*
    Position(SourceFile.current, start, end)
  }

  def withEnd(f: Int => Int): Span = copy(end = f(end))
}

private[ducktape] object Span {
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
