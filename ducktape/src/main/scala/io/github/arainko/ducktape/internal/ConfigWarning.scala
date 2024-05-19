package io.github.arainko.ducktape.internal

import scala.quoted.Quotes

private[ducktape] final case class ConfigWarning(span: Span, overriderSpan: Span, path: Path) {
  def render(using Quotes): String = {
    val pos = overriderSpan.withEnd(_ - 1).toPosition
    val codeAndLocation = s"${pos.sourceCode.mkString} @ ${pos.sourceFile.name}:${pos.endLine + 1}:${pos.endColumn + 1}"

    s"Config for ${path.render} is being overriden by $codeAndLocation"
  }
}

private[ducktape] object ConfigWarning {
  def renderAll(warnings: List[ConfigWarning])(using Quotes) =
    warnings
      .groupBy(_.overriderSpan)
      .map { (overriderSpan, warnings) =>
        val pos = overriderSpan.withEnd(_ - 1).toPosition
        val codeAndLocation = s"${pos.sourceCode.mkString} @ ${pos.sourceFile.name}:${pos.endLine + 1}:${pos.endColumn + 1}"

        if warnings.size > 1 then s"""Configs for:
        |${warnings.map(warning => "  * " + warning.path.render).mkString(System.lineSeparator)}
        |are being overriden by $codeAndLocation""".stripMargin
        else warnings.map(_.render).mkString
      }
}
