package io.github.arainko.ducktape.docs

import org.scalafmt.dynamic.ConsoleScalafmtReporter
import org.scalafmt.interfaces.Scalafmt

import java.io.{ OutputStream, PrintStream }
import java.nio.file.Path as JPath
import scala.quoted.*
import scala.util.chaining.*
import java.time.Instant
import io.github.arainko.ducktape.internal.*

/**
 * Sometimes the code printed with `Printer.TreeShortCode` is not fully legal (at least in scalafmt terms)
 * so we create an error reporter that doesn't report anything to not bother ourselves with weird stacktraces when the docs are compiling
 */
object SilentReporter extends ConsoleScalafmtReporter(PrintStream(OutputStream.nullOutputStream()))

object Docs {
  val scalafmt = Scalafmt.create(this.getClass.getClassLoader()).withReporter(SilentReporter)
  val config = JPath.of(".scalafmt.conf")

  inline def printStructure[A]: Unit = ${ printStructureMacro[A] }

  def printStructureMacro[A: Type](using Quotes) = {
    val struct = Structure.of[A](Path.empty(Type.of[A]))
    val markdown =  format(stripColors(Debug.show(struct)))
    '{
      println(${ Expr(markdown) })
    }
  }

  inline def printCode[A](inline value: A): A = ${ printCodeMacro[A]('value) }

  def printCodeMacro[A: Type](value: Expr[A])(using Quotes): Expr[A] = {
    import quotes.reflect.*

    /*
     * Printer.TreeShortCode prints type lambdas with a => instead of =>> which makes scalafmt REALLY, REALLY upset
     * this beauty fixes it until https://github.com/lampepfl/dotty/pull/16968 is merged to dotty
     */
    val typeLambdaSingleArgRegex = """\[(\[\p{L}+ >: Nothing <: \p{L}+\]) =>""".r
    val typeLambdaTwoArgsRegex = """\[(\[\p{L}+ >: Nothing <: \p{L}+\, \p{L}+ >: Nothing <: \p{L}+\]) =>""".r
    def fixTypeLambdas(input: String): String =
      typeLambdaSingleArgRegex
        .replaceAllIn(input, "[$1 =>>")
        .pipe(typeLambdaTwoArgsRegex.replaceAllIn(_, "[$1 =>>"))

    val struct = fixTypeLambdas(Printer.TreeShortCode.show(value.asTerm))

    '{
      println(${ Expr(format(struct)) })
      $value
    }
  }

  private def format(code: String) = {
    // we need to enclose it inside a class/object, otherwise scalafmt doesn't run
    val enclosedCode =
      s"""object Code {
      |  $code
      |}""".stripMargin

    scalafmt
      .format(config, JPath.of("Code.scala"), enclosedCode)
      .linesWithSeparators
      .toVector
      .drop(1) // strip 'object Code {'
      .dropRight(1) // strip the block-closing '}'
      .prepended("``` scala \n") // enclose it in a Scala markdown block
      .appended("```")
      .mkString
  }

  private def stripColors(string: String) = {
    // private def bold: String = s"${Console.BOLD}$self${Console.RESET}"
    // private def underlined: String = s"${Console.UNDERLINED}$self${Console.RESET}"
    // private def red: String = s"${Console.RED}$self${Console.RESET}"
    // private def magenta: String = s"${Console.MAGENTA}$self${Console.RESET}"
    // private def cyan: String = s"${Console.CYAN}$self${Console.RESET}"
    // private def yellow: String = s"${Console.YELLOW}$self${Console.RESET}"
    string
      .replace(Console.BOLD, "")
      .replace(Console.UNDERLINED, "")
      .replace(Console.RED, "")
      .replace(Console.MAGENTA, "")
      .replace(Console.CYAN, "")
      .replace(Console.YELLOW, "")
      .replace(Console.RESET, "")
  }
}
