package io.github.arainko.ducktape.internal.macros

import scala.compiletime.*
import scala.deriving.Mirror as DerivingMirror
import scala.quoted.*
import io.github.arainko.ducktape.Configuration.*
import io.github.arainko.ducktape.internal.modules.*
import io.github.arainko.ducktape.function.*

private[ducktape] class BuilderMacros[
  F[_, _, _ <: Tuple]: Type,
  From: Type,
  To: Type,
  Config <: Tuple: Type
](private val builder: Expr[F[From, To, Config]])(using val quotes: Quotes)
    extends Module,
      MirrorModule,
      FieldModule,
      SelectorModule {
  import quotes.reflect.*
  import BuilderMacros.*

  def withConfigEntryForField[ConfigEntry[_ <: String]: Type](
    lambdaSelector: Expr[To => ?]
  )(using DerivingMirror.ProductOf[To]) = {
    selectedField(lambdaSelector).asConstantType match {
      case '[IsString[selectedField]] =>
        '{ $builder.asInstanceOf[F[From, To, ConfigEntry[selectedField] *: Product.RemoveByLabel[selectedField, Config]]] }
    }
  }

  def withConfigEntryForFields[ConfigEntry[_ <: String, _ <: String]: Type](to: Expr[To => ?], from: Expr[From => ?])(using
    DerivingMirror.ProductOf[From],
    DerivingMirror.ProductOf[To]
  ) = {
    val firstField = selectedField(from).asConstantType
    val secondField = selectedField(to).asConstantType
    (secondField, firstField) match {
      case ('[IsString[firstField]], '[IsString[secondField]]) =>
        '{ $builder.asInstanceOf[F[From, To, ConfigEntry[firstField, secondField] *: Product.RemoveByLabel[firstField, Config]]] }
    }
  }

  def withConfigEntryForInstance[Instance: Type](using DerivingMirror.SumOf[From]) =
    '{ $builder.asInstanceOf[F[From, To, Coproduct.Instance[Instance] *: Coproduct.RemoveByType[Instance, Config]]] }

  def withConfigEntryForArg[NamedArgs <: Tuple: Type, ConfigEntry[_ <: String]: Type](
    argSelector: Expr[FunctionArguments[NamedArgs] => ?]
  ) = selectedArg(argSelector).asConstantType match {
    case '[IsString[selectedArg]] =>
      '{ $builder.asInstanceOf[F[From, To, ConfigEntry[selectedArg] *: Product.RemoveByLabel[selectedArg, Config]]] }
  }

  def withConfigEntryForArgAndField[NamedArgs <: Tuple: Type, ConfigEntry[_ <: String, _ <: String]: Type](
    argSelector: Expr[FunctionArguments[NamedArgs] => ?],
    lambdaSelector: Expr[From => ?]
  )(using DerivingMirror.ProductOf[From]) = {
    val arg = selectedArg(argSelector).asConstantType
    val field = selectedField(lambdaSelector).asConstantType
    (arg, field) match {
      case ('[IsString[arg]], '[IsString[field]]) =>
        '{ $builder.asInstanceOf[F[From, To, ConfigEntry[arg, field] *: Product.RemoveByLabel[field, Config]]] }
    }
  }

  extension (value: String) {
    def asConstantType: Type[? <: AnyKind] = ConstantType(StringConstant(value)).asType
  }
}

private[ducktape] object BuilderMacros {
  private type IsString[S <: String] = S

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
  )(using From: DerivingMirror.ProductOf[From], To: DerivingMirror.ProductOf[To]) =
    ${ withConfigEntryForFieldsMacro[F, From, To, Config, ConfigEntry]('builder, 'to, 'from)(using 'From, 'To) }

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
  )(using Expr[DerivingMirror.ProductOf[From]], Expr[DerivingMirror.ProductOf[To]])(using Quotes) =
    BuilderMacros(builder).withConfigEntryForFields[ConfigEntry](to, from)

  transparent inline def withConfigEntryForField[
    F[_, _, _ <: Tuple],
    From,
    To,
    Config <: Tuple,
    ConfigEntry[_ <: String]
  ](
    builder: F[From, To, Config],
    inline selector: To => ?
  )(using To: DerivingMirror.ProductOf[To]) = ${
    withConfigEntryForFieldMacro[F, From, To, Config, ConfigEntry]('builder, 'selector)(using 'To)
  }

  def withConfigEntryForFieldMacro[
    F[_, _, _ <: Tuple]: Type,
    From: Type,
    To: Type,
    Config <: Tuple: Type,
    ConfigEntry[_ <: String]: Type
  ](
    builder: Expr[F[From, To, Config]],
    lambda: Expr[To => ?]
  )(using Expr[DerivingMirror.ProductOf[To]])(using Quotes) =
    BuilderMacros(builder).withConfigEntryForField[ConfigEntry](lambda)

  transparent inline def withConfigEntryForInstance[
    F[_, _, _ <: Tuple],
    From,
    To,
    Config <: Tuple,
    Instance <: From
  ](builder: F[From, To, Config])(using From: DerivingMirror.SumOf[From]) =
    ${ withConfigEntryForInstanceMacro[F, From, To, Config, Instance]('builder)(using 'From) }

  def withConfigEntryForInstanceMacro[
    F[_, _, _ <: Tuple]: Type,
    From: Type,
    To: Type,
    Config <: Tuple: Type,
    Instance: Type
  ](
    builder: Expr[F[From, To, Config]]
  )(using Expr[DerivingMirror.SumOf[From]])(using Quotes) =
    BuilderMacros(builder).withConfigEntryForInstance[Instance]

  transparent inline def withConfigEntryForArg[
    F[_, _, _ <: Tuple],
    From,
    To,
    Config <: Tuple,
    NamedArgs <: Tuple,
    ConfigEntry[_ <: String]
  ](
    builder: F[From, To, Config],
    inline argSelector: FunctionArguments[NamedArgs] => ?
  ) =
    ${ withConfigEntryForArgMacro[F, From, To, Config, NamedArgs, ConfigEntry]('builder, 'argSelector) }

  def withConfigEntryForArgMacro[
    F[_, _, _ <: Tuple]: Type,
    From: Type,
    To: Type,
    Config <: Tuple: Type,
    NamedArgs <: Tuple: Type,
    ConfigEntry[_ <: String]: Type
  ](
    builder: Expr[F[From, To, Config]],
    argSelector: Expr[FunctionArguments[NamedArgs] => ?]
  )(using Quotes) =
    BuilderMacros(builder).withConfigEntryForArg[NamedArgs, ConfigEntry](argSelector)

  transparent inline def withConfigEntryForArgAndField[
    F[_, _, _ <: Tuple],
    From,
    To,
    Config <: Tuple,
    NamedArgs <: Tuple,
    ConfigEntry[_ <: String, _ <: String]
  ](
    builder: F[From, To, Config],
    inline argSelector: FunctionArguments[NamedArgs] => ?,
    inline fieldSelector: From => ?
  )(using From: DerivingMirror.ProductOf[From]) =
    ${
      withConfigEntryForArgAndFieldMacro[F, From, To, Config, NamedArgs, ConfigEntry](
        'builder,
        'argSelector,
        'fieldSelector
      )(using 'From)
    }

  def withConfigEntryForArgAndFieldMacro[
    F[_, _, _ <: Tuple]: Type,
    From: Type,
    To: Type,
    Config <: Tuple: Type,
    NamedArgs <: Tuple: Type,
    ConfigEntry[_ <: String, _ <: String]: Type
  ](
    builder: Expr[F[From, To, Config]],
    argSelector: Expr[FunctionArguments[NamedArgs] => ?],
    fieldSelector: Expr[From => ?]
  )(using Expr[DerivingMirror.ProductOf[From]])(using Quotes) =
    BuilderMacros(builder).withConfigEntryForArgAndField[NamedArgs, ConfigEntry](argSelector, fieldSelector)

}
