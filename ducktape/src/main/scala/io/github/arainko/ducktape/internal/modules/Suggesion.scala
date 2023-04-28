package io.github.arainko.ducktape.internal.modules

private[ducktape] opaque type Suggestion = String

private[ducktape] object Suggestion {
  def apply(text: String): Suggestion = text

  def all(head: String, tail: String*): List[Suggestion] = head :: tail.toList

  def fromFields(fields: Fields): List[Suggestion] = fields.value.map(f => Suggestion(s"_.${f.name}"))

  /**
   * Prepends a newline, adds a '|' (to work with .stripPrefix) and a bullet point character to each suggestion.
   */
  def renderAll(suggestions: List[Suggestion]): String =
    suggestions.mkString("\n| • ", "\n| • ", "")
}
