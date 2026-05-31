package io.github.arainko.ducktape

/**
 * `Renamer` is a DSL designed to describe name transformations on fields and cases.
 *
 * Its compiletime nature means that all the operations and the arguments to those operations need to be known at compiletime, for example:
 *
 * {{{
 * (renamer: Renamer) => renamer.toUpperCase.rename("fromThis", "toThat")
 * }}}
 *
 * ...is a valid renamer expression that can be lifted at compiletime, while something like:
 * {{{
 * val dynamicString = Random.nextString(10)
 * (renamer: Renamer) => renamer.toUpperCase.rename("fromThis", dynamicString)
 * }}}
 *
 * ...is not since the value of `dynamicString` is not known at compiletime.
 */
sealed trait Renamer {

  /**
   * Equivalent to `String#toUpperCase`
   */
  def toUpperCase: Renamer

  /**
   * Equivalent to `String#toLowerCase`
   */
  def toLowerCase: Renamer

  /**
   * Equivalent to the function `(str: String) => if str == from then to else str`
   */
  def rename(from: String, to: String): Renamer

  /**
   * Equivalent to `String#replace(target, replacement)`
   */
  def replace(target: String, replacement: String): Renamer

  /**
   * Equivalent to the function `(str: String) => Pattern.compile(pattern).matcher(str).replaceAll(replacement)`
   */
  def regexReplace(pattern: String, replacement: String): Renamer

  /**
   * Equivalent to the function `(str: String) => Pattern.compile(pattern).matcher(str).replaceAll(replacement: MatchGroup => String)`
   */
  def regexReplace(pattern: String, replacement: Renamer => Renamer): Renamer

  /**
   * Equivalent to `String#stripPrefix(prefix)`
   */
  def stripPrefix(prefix: String): Renamer

  /**
   * Equivalent to `String#stripSuffix(suffix)`
   */
  def stripSuffix(suffix: String): Renamer

  /**
   * Equivalent to `String#capitalize`
   */
  def capitalize: Renamer
}

object Renamer {
  // The snake-case-to-camel-case and kebab-case-to-camel-case transformation regexes were copied from circe-generic-extras:
  // https://github.com/circe/circe-generic-extras/blob/2e103585b26ec30619a7de33ff15122344f041f3/generic-extras/src/main/scala/io/circe/generic/extras/Configuration.scala#L86
  object camelCase {

    /**
     * Transforms a field name from camelCase to kebab-case
     */
    transparent inline def toKebabCase(inline fieldName: Renamer): Renamer =
      fieldName
        .regexReplace("([A-Z]+)([A-Z][a-z])", "$1-$2")
        .regexReplace("([a-z\\d])([A-Z])", "$1-$2")
        .toLowerCase

    /**
     * Transforms a field name from camelCase to snake_case
     */
    transparent inline def toSnakeCase(inline fieldName: Renamer): Renamer =
      fieldName
        .regexReplace("([A-Z]+)([A-Z][a-z])", "$1_$2")
        .regexReplace("([a-z\\d])([A-Z])", "$1_$2")
        .toLowerCase
  }

  object snakeCase {

    /**
     * Transforms a field from snake_case to camelCase
     */
    transparent inline def toCamelCase(inline fieldName: Renamer): Renamer =
      fieldName
        .regexReplace("^_[a-zA-Z\\d]", _.replace("_", "").toLowerCase)
        .regexReplace("_[a-zA-Z\\d]", _.replace("_", "").toUpperCase)
  }

  object kebabCase {

    /**
     * Transforms a field from kebab-case to camelCase
     */
    transparent inline def toCamelCase(inline fieldName: Renamer): Renamer =
      fieldName
        .regexReplace("^-[a-zA-Z\\d]", _.replace("-", "").toLowerCase)
        .regexReplace("-[a-zA-Z\\d]", _.replace("-", "").toUpperCase)
  }
}
