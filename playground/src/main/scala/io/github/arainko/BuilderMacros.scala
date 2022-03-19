package io.github.arainko

import scala.compiletime.*
import scala.deriving.Mirror
import scala.quoted.*

class BuilderMacros[
  F[_, _, _ <: Tuple]: Type,
  From: Type,
  To: Type,
  Config <: Tuple: Type
](val builder: Expr[F[From, To, Config]])(using val quotes: Quotes) {
  import quotes.reflect.*
  import BuilderMacros.*

  def withConfigEntryForField[ConfigEntry[_ <: String]: Type](lambdaSelector: Expr[From => ?]) = {
    selector.selectedField(lambdaSelector).asConstantType match {
      case '[IsString[selectedField]] =>
        '{ $builder.asInstanceOf[F[From, To, ConfigEntry[selectedField] *: Config]] }
    }
  }

  def withConfigEntryForFields[ConfigEntry[_ <: String, _ <: String]: Type](first: Expr[From => ?], second: Expr[From => ?]) = {
    val firstField = selector.selectedField(first).asConstantType
    val secondField = selector.selectedField(second).asConstantType
    (firstField, secondField) match {
      case ('[IsString[firstField]], '[IsString[secondField]]) =>
        '{ $builder.asInstanceOf[F[From, To, ConfigEntry[firstField, secondField] *: Config]] }
    }
  }

  private val selector = SelectorMacros()

  extension (value: String) {
    def asConstantType: Type[? <: AnyKind] = ConstantType(StringConstant(value)).asType
  }
}

object BuilderMacros {
  private type IsString[S <: String] = S

  transparent inline def withConfigEntryForField[
    F[_, _, _ <: Tuple],
    From,
    To,
    Config <: Tuple,
    ConfigEntry[_ <: String]
  ](
    builder: F[From, To, Config],
    inline selector: From => ?
  ) = ${ withConfigEntryForFieldMacro[F, From, To, Config, ConfigEntry]('builder, 'selector) }

  def withConfigEntryForFieldMacro[
    F[_, _, _ <: Tuple]: Type,
    From: Type,
    To: Type,
    Config <: Tuple: Type,
    ConfigEntry[_ <: String]: Type
  ](
    builder: Expr[F[From, To, Config]],
    lambda: Expr[From => ?]
  )(using Quotes) = BuilderMacros(builder).withConfigEntryForField[ConfigEntry](lambda)
}
