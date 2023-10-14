package io.github.arainko.ducktape.internal

import scala.compiletime.*
import scala.quoted.*
import io.github.arainko.ducktape.Transformer
import io.github.arainko.tooling.FullName

private[ducktape] object Logger {

  transparent inline given Level = Level.Info
  given Output = Output.StdOut

  enum Level {
    case Debug, Info, Off
  }

  enum Output {
    case StdOut, Report

    final def print(msg: String)(using Quotes) =
      this match {
        case StdOut => println(msg)
        case Report => quotes.reflect.report.info(msg)
      }
  }

  private val infoTag = s"${Console.GREEN}[INFO]${Console.RESET}"
  private val debugTag = s"${Console.GREEN}[DEBUG]${Console.RESET}"
  private def blue(msg: String) = s"${Console.BLUE}$msg${Console.RESET}"

  inline def loggedInfo[A](using Level, Output, FullName, Debug[A], Quotes)(
    inline msg: String
  )(value: A) = {
    info(msg, value)
    value
  }

  inline def info(inline msg: String)(using level: Level, output: Output, name: FullName, quotes: Quotes): Unit =
    inline level match {
      case Level.Debug => ()
      case Level.Info  => output.print(s"$infoTag $msg ${blue(s"[$name]")}")
      case Level.Off   => ()
    }

  inline def info[A](
    inline msg: String,
    value: A
  )(using Level, Output, FullName, Debug[A], Quotes): Unit =
    info(s"$msg: ${Debug.show(value)}")

  inline def loggedDebug[A](using Level, Output, FullName, Debug[A], Quotes)(
    inline msg: String
  )(value: A) = {
    debug(msg, value)
    value
  }

  inline def debug(inline msg: String)(using level: Level, output: Output, name: FullName, quotes: Quotes): Unit =
    inline level match {
      case Level.Debug => output.print(s"$debugTag $msg ${blue(s"[$name]")}")
      case Level.Info  => output.print(s"$infoTag $msg ${blue(s"[$name]")}")
      case Level.Off   => ()
    }

  inline def debug[A](inline msg: String, value: A)(using level: Level, name: FullName, _debug: Debug[A], quotes: Quotes): Unit =
    debug(s"$msg: ${Debug.show(value)}")
}
