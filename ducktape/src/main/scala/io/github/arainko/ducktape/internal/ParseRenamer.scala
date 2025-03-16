package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.Renamer

import java.util.regex.Pattern
import scala.annotation.tailrec
import scala.quoted.*

private[ducktape] object ParseRenamer {
  def parse(expr: Expr[Renamer => Renamer])(using Quotes): String => String = {
    import quotes.reflect.*

    @tailrec
    def recurse(
      current: Expr[Renamer => Renamer],
      accumulatedFunctions: List[String => String]
    )(using Quotes): List[String => String] = {
      current match {
        case '{ (arg: Renamer) => arg } => accumulatedFunctions

        case '{ (arg: Renamer) => ($body(arg): Renamer).toUpperCase } =>
          recurse(body, ((str: String) => str.toUpperCase) :: accumulatedFunctions)

        case '{ (arg: Renamer) => ($body(arg): Renamer).toLowerCase } =>
          recurse(body, ((str: String) => str.toLowerCase) :: accumulatedFunctions)

        case '{ (arg: Renamer) => ($body(arg): Renamer).rename(${ Expr(from) }, ${ Expr(to) }) } =>
          recurse(body, ((str: String) => if str == from then to else str) :: accumulatedFunctions)

        case '{ (arg: Renamer) => ($body(arg): Renamer).replace(${ Expr(from) }, ${ Expr(to) }) } =>
          recurse(body, ((str: String) => str.replace(from, to)) :: accumulatedFunctions)

        case '{ (arg: Renamer) => ($body(arg): Renamer).regexReplace(${ Expr(pattern) }, ${ Expr(replacement) }) } =>
          recurse(
            body, {
              val regex = Pattern.compile(pattern)
              (str: String) => regex.matcher(str).replaceAll(replacement)
            } :: accumulatedFunctions
          )

        case '{ (arg: Renamer) => ($body(arg): Renamer).stripPrefix(${ Expr(prefix) }) } =>
          recurse(body, ((str: String) => str.stripPrefix(prefix)) :: accumulatedFunctions)

        case '{ (arg: Renamer) => ($body(arg): Renamer).stripSuffix(${ Expr(suffix) }) } =>
          recurse(body, ((str: String) => str.stripSuffix(suffix)) :: accumulatedFunctions)

        case '{ (arg: Renamer) => ($body(arg): Renamer).capitalize } =>
          recurse(body, ((str: String) => str.capitalize) :: accumulatedFunctions)

        case _ =>
          report.errorAndAbort(
            "Invalid renamer expression - make sure all of the renamer expressions can be read at compiletime",
            expr
          )
      }
    }

    val renameFunctions = recurse(expr, Nil)
    scala.Function.chain(renameFunctions)
  }
}
