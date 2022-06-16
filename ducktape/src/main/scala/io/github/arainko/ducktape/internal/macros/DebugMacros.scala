package io.github.arainko.ducktape.internal.macros

import scala.quoted.*
import io.github.arainko.ducktape.*

object DebugMacros {
  inline def structure[A](inline value: A) = ${ structureMacro('value) }

  def structureMacro[A: Type](value: Expr[A])(using Quotes) = {
    import quotes.reflect.*
    val struct = Printer.TreeStructure.show(value.asTerm)
    '{
      println(${ Expr(struct) })
      $value
    }
  }

  inline def code[A](inline value: A) = ${ codeMacro('value) }

  def codeMacro[A: Type](value: Expr[A])(using Quotes) = {
    import quotes.reflect.*

    val struct = Printer.TreeShortCode.show(value.asTerm)
    '{
      println(${ Expr(struct) })
      $value
    }
  }

  inline def codeCompiletime[A](inline value: A) = ${ codeCompiletimeMacro('value) }

  def codeCompiletimeMacro[A: Type](value: Expr[A])(using Quotes) = {
    import quotes.reflect.*

    val struct = Printer.TreeShortCode.show(value.asTerm)
    println(struct)

    value
  }
}

object Config

opaque type TypedConfig[A] = Config.type

object TypedConfig {
  def const[Source, FieldType, Actual](selector: Source => FieldType, const: Actual)(using
    Actual <:< FieldType
  ): TypedConfig[Source] = Config
}
