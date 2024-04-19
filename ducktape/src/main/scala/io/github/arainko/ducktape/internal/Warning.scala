package io.github.arainko.ducktape.internal

import scala.quoted.Quotes
import io.github.arainko.ducktape.internal.Path.Segment

private[ducktape] final case class Warning(span: Span, overriderSpan: Span, path: Path) {
  def render(using Quotes): String = {
    val pos = overriderSpan.toPosition
    val codeAndLocation = s"${pos.sourceCode.mkString} @ ${pos.sourceFile.name}:${pos.endLine + 1}:${pos.endColumn + 1}"

    s"Config for ${path.render} is being overriden by $codeAndLocation"
  }
}
