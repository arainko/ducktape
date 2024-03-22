package io.github.arainko.ducktape.docs

import org.scalafmt.dynamic.ConsoleScalafmtReporter
import org.scalafmt.interfaces.Scalafmt

import java.io.{ OutputStream, PrintStream }
import java.nio.file.{ Files, Path }
import java.time.Instant
import java.util.stream.Collectors
import scala.quoted.*
import scala.util.chaining.*


object Docs {
  val scalafmt = Scalafmt.create(this.getClass.getClassLoader())
  val config = Path.of(".scalafmt.conf")

  inline def printCode[A](inline value: A): A = ${ printCodeMacro[A]('value) }

  def printCodeMacro[A: Type](value: Expr[A])(using Quotes): Expr[A] = {
    import quotes.reflect.*

    /*
     * Printer.TreeShortCode prints type lambdas with a => instead of =>> which makes scalafmt REALLY, REALLY upset
     * this beauty fixes it until https://github.com/lampepfl/dotty/pull/16968 is merged to dotty
     */
    val typeLambdaSingleArgRegex = """\[(\[\p{L}+ >: Nothing <: \p{L}+\]) =>""".r
    val typeLambdaTwoArgsRegex = """\[(\[\p{L}+ >: Nothing <: \p{L}+\, \p{L}+ >: Nothing <: \p{L}+\]) =>""".r
    val typeLambdaInsideRegex = """(\[\p{L}+ >: Nothing <: \p{L}+\]) =>\s""".r
    def fixTypeLambdas(input: String): String =
      typeLambdaSingleArgRegex
        .replaceAllIn(input, "[$1 =>>")
        .pipe(typeLambdaTwoArgsRegex.replaceAllIn(_, "[$1 =>>"))
        .pipe(typeLambdaInsideRegex.replaceAllIn(_, "$1 =>> "))

    val struct = fixTypeLambdas(Printer.TreeShortCode.show(value.asTerm))

    // we need to enclose it inside a class/object, otherwise scalafmt doesn't run
    val enclosedCode =
      s"""object Code {
      |  $struct
      |}""".stripMargin

    val formatted =
      scalafmt
        .format(config, Path.of("Code.scala"), enclosedCode)
        .linesWithSeparators
        .toVector
        .drop(1) // strip 'object Code {'
        .dropRight(1) // strip the block-closing '}'
        .map(_.drop(2)) // drop two spaces of dangling indent
        .prepended("``` scala \n") // enclose it in a Scala markdown block
        .appended("```")
        .mkString

    '{
      println(${ Expr(formatted) })
      $value
    }
  }

  inline def members[A] = ${ membersOf[A] }

  private def membersOf[A: Type](using Quotes) = {
    import quotes.reflect.*

    val members =
      TypeRepr
        .of[A]
        .typeSymbol
        .declaredMethods
        .map(meth => TypeRepr.of[A].memberType(meth))
        .map {
          case MethodType(params, tpes, ret) => s"params -> $params, argTypes -> ${tpes.map(_.show)}, ret -> ${ret.show}"
          case PolyType(names, bounds, MethodType(params, tpes, ret)) =>
            s"params -> $params, argTypes -> ${tpes.map(_.show)}, ret -> ${ret.show}"
          case other => other.show(using Printer.TypeReprStructure)
        }

    report.info(members.mkString("\n"))
    '{}
  }
}
