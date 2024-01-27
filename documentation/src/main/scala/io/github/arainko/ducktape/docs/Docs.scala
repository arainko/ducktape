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
  val scalafmt = Scalafmt.create(this.getClass.getClassLoader())//.withReporter(SilentReporter)
  val config = JPath.of(".scalafmt.conf")

  inline def printPlan[A, B]: Unit = ${ printPlanMacro[A, B] }

  def printPlanMacro[A: Type, B: Type](using Quotes) = {
    given Debug[Structure] with {
      extension (self: Structure) def show(using Quotes): String = {
        import quotes.reflect.*
        s"Structure.of[${self.tpe.repr.show(using Printer.TypeReprShortCode)}]"
      }
    }
    given customizedPlanDebug: Debug[Plan[Plan.Error]] = 
      Debug.derived[Plan[Plan.Error]]

    val sourceStruct = Structure.of[A](Path.empty(Type.of[A]))
    val destStruct = Structure.of[B](Path.empty(Type.of[B]))

    given TransformationSite = TransformationSite.Transformation
    val plan = Planner.between(sourceStruct, destStruct)
    val printed = format(stripColors(customizedPlanDebug.show(plan)))
    '{
      println(${ Expr(printed) })
    }
  }

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

  private def format(code: String, maxColumn: Int = 80) = {
    // we need to enclose it inside a class/object, otherwise scalafmt doesn't run
    val enclosedCode =
      s"""|// scalafmt: { maxColumn = $maxColumn }
      |object Code {
      |  $code
      |}""".stripMargin

    scalafmt
      .format(config, JPath.of("Code.scala"), enclosedCode)
      .linesWithSeparators
      .toVector
      .drop(2) // strip 'object Code {'
      .dropRight(1) // strip the block-closing '}'
      .prepended("``` scala \n") // enclose it in a Scala markdown block
      .appended("```")
      .mkString
  }

  private def stripColors(string: String) = {
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
