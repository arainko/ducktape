package io.github.arainko.ducktape.internal

import scala.quoted.*
import io.github.arainko.ducktape.Renamer

private[ducktape] object ParseRenamer {
  def parse(expr: Expr[Renamer => Renamer])(using Quotes): String => String = {
    import quotes.reflect.*

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

        case '{ (arg: Renamer) => ($body(arg): Renamer).regexReplace(${ Expr(from) }, ${ Expr(to) }) } =>
          recurse(body, ((str: String) => str.replaceAll(from, to)) :: accumulatedFunctions)          
        
        case _ => report.errorAndAbort("Invalid renamer expression - make sure all of the renamer expressions can be read at compiletime", expr)
      }
    }
    
    val renameFunctions = recurse(expr, Nil)
    scala.Function.chain(renameFunctions)
  }
}
