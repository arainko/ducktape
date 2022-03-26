package io.github.arainko

import scala.compiletime.*
import scala.deriving.Mirror
import scala.quoted.*
import io.github.arainko.Configuration.*

class BuilderMacros[
  F[_, _, _ <: Tuple]: Type,
  From: Type,
  To: Type,
  Config <: Tuple: Type
](val builder: Expr[F[From, To, Config]])(using val quotes: Quotes) {
  import quotes.reflect.*
  import BuilderMacros.*

  def withConfigEntryForField[ConfigEntry[_ <: String]: Type](lambdaSelector: Expr[To => ?]) = {
    selector.selectedField(lambdaSelector).asConstantType match {
      case '[IsString[selectedField]] =>
        '{ $builder.asInstanceOf[F[From, To, ConfigEntry[selectedField] *: RemoveByLabel[selectedField, Config]]] }
    }
  }

  def withConfigEntryForFields[ConfigEntry[_ <: String, _ <: String]: Type](to: Expr[To => ?], from: Expr[From => ?]) = {
    val firstField = selector.selectedField(from).asConstantType
    val secondField = selector.selectedField(to).asConstantType
    (secondField, firstField) match {
      case ('[IsString[firstField]], '[IsString[secondField]]) =>
        '{ $builder.asInstanceOf[F[From, To, ConfigEntry[firstField, secondField] *: RemoveByLabel[firstField, Config]]] }
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
    inline selector: To => ?
  ) = ${ withConfigEntryForFieldMacro[F, From, To, Config, ConfigEntry]('builder, 'selector) }

  transparent inline def withConfigEntryForFields[
    F[_, _, _ <: Tuple],
    From,
    To,
    Config <: Tuple,
    ConfigEntry[_ <: String, _ <: String]
  ](
    builder: F[From, To, Config],
    inline to: To => ?,
    inline from: From => ?
  ) = ${ withConfigEntryForFieldsMacro[F, From, To, Config, ConfigEntry]('builder, 'to, 'from) }

  def withConfigEntryForFieldsMacro[
    F[_, _, _ <: Tuple]: Type,
    From: Type,
    To: Type,
    Config <: Tuple: Type,
    ConfigEntry[_ <: String, _ <: String]: Type
  ](
    builder: Expr[F[From, To, Config]],
    to: Expr[To => ?],
    from: Expr[From => ?]
  )(using q: Quotes) = BuilderMacros(builder).withConfigEntryForFields[ConfigEntry](to, from)

  def withConfigEntryForFieldMacro[
    F[_, _, _ <: Tuple]: Type,
    From: Type,
    To: Type,
    Config <: Tuple: Type,
    ConfigEntry[_ <: String]: Type
  ](
    builder: Expr[F[From, To, Config]],
    lambda: Expr[To => ?]
  )(using Quotes) = BuilderMacros(builder).withConfigEntryForField[ConfigEntry](lambda)
}
