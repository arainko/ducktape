package io.github.arainko.ducktape.docs

import scala.quoted.*
import org.scalafmt.interfaces.Scalafmt
import java.nio.file.Path
import org.scalafmt.dynamic.ConsoleScalafmtReporter
import java.io.PrintStream
import java.io.OutputStream

/**
 * Sometimes the code printed with `Printer.TreeShortCode` is not fully legal (at least in scalafmt terms)
 * so we create an error reporter that doesn't report anything to not bother ourselves with weird stacktraces when the docs are compiling
 */
object SilentReporter extends ConsoleScalafmtReporter(PrintStream(OutputStream.nullOutputStream()))

object Docs {
  val scalafmt = Scalafmt.create(this.getClass.getClassLoader()).withReporter(SilentReporter)
  val config = Path.of(".scalafmt.conf")

  inline def printCode[A](inline value: A): A = ${ printCodeMacro[A]('value) }

  def printCodeMacro[A: Type](value: Expr[A])(using Quotes): Expr[A] = {
    import quotes.reflect.*

    /*
     * Printer.TreeShortCode prints type lambdas with a => instead of =>> which makes scalafmt REALLY, REALLY upset
     * this beauty fixes it until https://github.com/lampepfl/dotty/pull/16968 is merged to dotty
     */
    val typeLambdaRegex = """\[(\[\p{L}+ >: Nothing <: \p{L}+\]) =>""".r
    def fixTypeLambdas(input: String): String = typeLambdaRegex.replaceAllIn(input, "[$1 =>>")

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
        .prepended("``` scala \n") // enclose it in a Scala markdown block
        .appended("```")
        .mkString

    '{
      println(${ Expr(formatted) })
      $value
    }
  }
}
