package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.internal.{ Debug, Metainformation }

import scala.Ordering.Implicits.*
import scala.compiletime.*
import scala.quoted.*

private[ducktape] object Logger {

  // Logger Config
  private transparent inline def level = Level.Debug
  private val output = Output.StdOut
  private def filter(msg: String, meta: Metainformation) = meta.contains("Path") || meta.contains("Trans")

  enum Level {
    case Off, Debug, Info
  }

  object Level {
    given Ordering[Level] = Ordering.by(_.ordinal)
  }

  enum Output {
    case StdOut, Report

    final def print(msg: String, level: Level, meta: Metainformation)(using Quotes) = {
      def colored(color: String & Singleton)(msg: String) = s"$color$msg${Console.RESET}"
      def blue(msg: String) = colored(Console.BLUE)(msg)
      def green(msg: String) = colored(Console.GREEN)(msg)
      val formatted = s"${green(s"[${level.toString().toUpperCase()}]")} $msg ${blue(s"[$meta]")}"
      this match {
        case StdOut => if (filter(msg, meta)) println(formatted)
        case Report => if (filter(msg, meta)) quotes.reflect.report.info(formatted)
      }
    }
  }

  inline def loggedInfo[A](using Metainformation, Quotes)(
    inline msg: String
  )(value: A)(using Debug[A]) = {
    info(msg, value)
    value
  }

  inline def info(inline msg: String)(using name: Metainformation, quotes: Quotes): Unit =
    inline level match {
      case Level.Debug => if (level <= Level.Info) output.print(msg, Level.Info, name)
      case Level.Info  => if (level <= Level.Info) output.print(msg, Level.Info, name)
      case Level.Off   => ()
    }

  inline def info[A](
    inline msg: String,
    value: A
  )(using Metainformation, Debug[A], Quotes): Unit =
    info(s"$msg: ${Debug.show(value)}")

  inline def loggedDebug[A](using Metainformation, Quotes)(
    inline msg: String
  )(value: A)(using Debug[A]) = {
    debug(msg, value)
    value
  }

  inline def debug(inline msg: String)(using name: Metainformation, quotes: Quotes): Unit =
    inline level match {
      case Level.Debug => if (level <= Level.Debug) output.print(msg, Level.Debug, name)
      case Level.Info  => if (level <= Level.Debug) output.print(msg, Level.Debug, name)
      case Level.Off   => ()
    }

  inline def debug[A](inline msg: String, value: A)(using
    name: Metainformation,
    _debug: Debug[A],
    quotes: Quotes
  ): Unit =
    debug(s"$msg: ${Debug.show(value)}")
}
