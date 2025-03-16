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
