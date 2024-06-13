package io.github.arainko.ducktape.internal

import scala.Ordering.Implicits.*
import scala.quoted.*

private[ducktape] object Logger {

  // Logger Config
  private[ducktape] transparent inline given level: Level = Level.Debug
  private val output = Output.StdOut
  private def filter(msg: String) = true

  enum Level {
    case Off, Debug, Info
  }

  object Level {
    given Ordering[Level] = Ordering.by(_.ordinal)
  }

  enum Output {
    case StdOut, Report

    final def print(msg: String, level: Level)(using Quotes) = {
      def colored(color: String & Singleton)(msg: String) = s"$color$msg${Console.RESET}"
      def green(msg: String) = colored(Console.GREEN)(msg)
      val formatted = s"${green(s"[${level.toString().toUpperCase()}]")} $msg"
      this match {
        case StdOut => if (filter(msg)) println(formatted)
        case Report => if (filter(msg)) quotes.reflect.report.info(formatted)
      }
    }
  }

  inline def loggedInfo[A](using Quotes)(
    inline msg: String
  )(value: A)(using Debug[A]) = {
    info(msg, value)
    value
  }

  inline def info(inline msg: String)(using quotes: Quotes): Unit =
    inline level match {
      case Level.Debug => if (level <= Level.Info) output.print(msg, Level.Info)
      case Level.Info  => if (level <= Level.Info) output.print(msg, Level.Info)
      case Level.Off   => ()
    }

  inline def info[A](
    inline msg: String,
    value: A
  )(using Debug[A], Quotes): Unit =
    info(s"$msg: ${Debug.show(value)}")

  inline def loggedDebug[A](using Quotes)(
    inline msg: String
  )(value: A)(using Debug[A]) = {
    debug(msg, value)
    value
  }

  inline def debug(inline msg: String)(using quotes: Quotes): Unit =
    inline level match {
      case Level.Debug => if (level <= Level.Debug) output.print(msg, Level.Debug)
      case Level.Info  => if (level <= Level.Debug) output.print(msg, Level.Debug)
      case Level.Off   => ()
    }

  inline def debug[A](inline msg: String, value: A)(using
    _debug: Debug[A],
    quotes: Quotes
  ): Unit =
    debug(s"$msg: ${Debug.show(value)}")
}
