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
    minSpan.map(min => Span(macroPos.start, min.start - 1)).getOrElse(Span.fromPosition(macroPos))
  }
}

// ProdTest1(Test1.Cos(Nested1(1)))
//     .into[ProdTest2]
//     .transform(
//       // Field2.const(_.test.at[Test2.Cos].int.additional, 1), // missing field
//       // Field.computed(_.test.at[Test2.Cos].int.additional, _.test.ordinal + 123),
//       Field.const(_.test.at[Test2.Cos].int.int, 123), // overriden field



     
