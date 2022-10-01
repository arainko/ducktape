package io.github.arainko.ducktape.internal.macros

import scala.quoted.*
import io.github.arainko.ducktape.internal.modules.*

class NormalizationMacros(using val quotes: Quotes) extends Module, NormalizationModule

object NormalizationMacros {
  inline def normalize[A](inline value: A): A = ${ normalizeMacro('value) }

  def normalizeMacro[A: Type](expr: Expr[A])(using Quotes): Expr[A] = NormalizationMacros().normalize(expr)
}
