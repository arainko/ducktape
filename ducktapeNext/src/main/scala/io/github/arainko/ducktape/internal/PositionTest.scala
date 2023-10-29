package io.github.arainko.ducktape.internal

import scala.quoted.*

object PositionTest {
  inline def positions(inline args: Any*) = ${ positionsMacro('args) }

  def positionsMacro(expr: Expr[Seq[Any]])(using Quotes) = {
    import quotes.reflect.*

    Position.ofMacroExpansion

    val Varargs(exprs) = expr: @unchecked

    val minPos = exprs.map(_.asTerm.pos).minBy(_.start)
    val calculatedPos =
      Position(SourceFile.current, Position.ofMacroExpansion.start, minPos.start - 1)
    report.error("ANOTHER MESSAGE", calculatedPos)
    exprs.zipWithIndex.foreach((expr, idx) => report.error(s"expr $idx", expr))
    '{}
  }
}
