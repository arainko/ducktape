package io.github.arainko.ducktape.internal.modules

import io.github.arainko.ducktape.function.FunctionArguments
import io.github.arainko.ducktape.{ Case => CaseConfig, Field => FieldConfig, * }

import scala.quoted.*

private[ducktape] sealed trait MaterializedConfiguration

private[ducktape] object MaterializedConfiguration {
  enum Product extends MaterializedConfiguration {
    val destFieldName: String

    case Const(destFieldName: String, value: Expr[Any])
    case Computed(destFieldName: String, fuction: Expr[Any => Any])
    case Renamed(destFieldName: String, sourceFieldName: String)
  }

  enum Coproduct extends MaterializedConfiguration {
    val tpe: Type[?]

    case Computed(tpe: Type[?], function: Expr[Any => Any])
    case Const(tpe: Type[?], value: Expr[Any])
  }

  def materializeProductConfig[Source, Dest](
    config: Expr[Seq[BuilderConfig[Source, Dest]]]
  )(using Quotes, Fields.Source, Fields.Dest): List[Product] =
    Varargs
      .unapply(config)
      .getOrElse(Failure.abort(Failure.UnsupportedConfig(config, Failure.ConfigType.Field)))
      .flatMap(materializeSingleProductConfig)
      .groupBy(_.destFieldName)
      .map((_, fieldConfigs) => fieldConfigs.last) // keep the last applied field config only
      .toList

  def materializeArgConfig[Source, Dest, ArgSelector <: FunctionArguments](
    config: Expr[Seq[ArgBuilderConfig[Source, Dest, ArgSelector]]]
  )(using Quotes, Fields.Source, Fields.Dest): List[Product] =
    Varargs
      .unapply(config)
      .getOrElse(Failure.abort(Failure.UnsupportedConfig(config, Failure.ConfigType.Arg)))
      .map(materializeSingleArgConfig)
      .groupBy(_.destFieldName)
      .map((_, fieldConfigs) => fieldConfigs.last) // keep the last applied field config only
      .toList

  def materializeCoproductConfig[Source, Dest](
    config: Expr[Seq[BuilderConfig[Source, Dest]]]
  )(using Quotes, Cases.Source, Cases.Dest): List[Coproduct] =
    Varargs
      .unapply(config)
      .getOrElse(Failure.abort(Failure.UnsupportedConfig(config, Failure.ConfigType.Case)))
      .map(materializeSingleCoproductConfig)
      .groupBy(_.tpe.fullName) // TODO: This is probably not the best way to do this (?)
      .map((_, fieldConfigs) => fieldConfigs.last) // keep the last applied field config only
      .toList

  private def materializeSingleProductConfig[Source, Dest](
    config: Expr[BuilderConfig[Source, Dest]]
  )(using Quotes, Fields.Source, Fields.Dest) = {
    import quotes.reflect.*

    config match {
      case '{ FieldConfig.const[source, dest, fieldType, actualType]($selector, $value)(using $ev1, $ev2, $ev3) } =>
        val name = Selectors.fieldName(Fields.dest, selector)
        Product.Const(name, value) :: Nil

      case '{ FieldConfig.computed[source, dest, fieldType, actualType]($selector, $function)(using $ev1, $ev2, $ev3) } =>
        val name = Selectors.fieldName(Fields.dest, selector)
        Product.Computed(name, function.asInstanceOf[Expr[Any => Any]]) :: Nil

      case '{ FieldConfig.renamed[source, dest, sourceFieldType, destFieldType]($destSelector, $sourceSelector)(using $ev1, $ev2, $ev3) } =>
        val destFieldName = Selectors.fieldName(Fields.dest, destSelector)
        val sourceFieldName = Selectors.fieldName(Fields.source, sourceSelector)
        Product.Renamed(destFieldName, sourceFieldName) :: Nil

      case '{ FieldConfig.default[source, dest, destFieldType]($destSelector)(using $ev1, $ev2) } =>
        val destFieldName = Selectors.fieldName(Fields.dest, destSelector)
        val field = Fields.dest.unsafeGet(destFieldName)
        val default = field.default.getOrElse(Failure.abort(Failure.DefaultMissing(field.name, Type.of[dest])))

        if (default.asTerm.tpe <:< TypeRepr.of(using field.tpe)) Product.Const(field.name, default) :: Nil
        else Failure.abort(Failure.InvalidDefaultType(field, Type.of[dest]))

      case config @ '{ FieldConfig.allMatching[source, dest, fieldSource]($fieldSource)(using $ev1, $ev2, $fieldSourceMirror) } =>
        val fieldSourceFields = Fields.Source.fromMirror(fieldSourceMirror)
        val fieldSourceTerm = fieldSource.asTerm
        val materializedConfig =
          fieldSourceFields.value.flatMap { sourceField =>
            Fields.dest.byName
              .get(sourceField.name)
              .filter(sourceField <:< _)
              .map(field => Product.Const(field.name, accessField(fieldSourceTerm, field)))
          }

        if (materializedConfig.isEmpty)
          Failure.abort(Failure.FieldSourceMatchesNoneOfDestFields(config, summon[Type[fieldSource]], summon[Type[dest]]))
        else materializedConfig

      case other => Failure.abort(Failure.UnsupportedConfig(other, Failure.ConfigType.Field))
    }
  }

  private def materializeSingleCoproductConfig[Source, Dest](config: Expr[BuilderConfig[Source, Dest]])(using Quotes) =
    config match {
      case '{ CaseConfig.computed[sourceSubtype].apply[source, dest]($function)(using $ev1, $ev2, $ev3) } =>
        Coproduct.Computed(summon[Type[sourceSubtype]], function.asInstanceOf[Expr[Any => Any]])

      case '{ CaseConfig.const[sourceSubtype].apply[source, dest]($value)(using $ev1, $ev2, $ev3) } =>
        Coproduct.Const(summon[Type[sourceSubtype]], value)

      case other => Failure.abort(Failure.UnsupportedConfig(other, Failure.ConfigType.Case))
    }

  private def materializeSingleArgConfig[Source, Dest, ArgSelector <: FunctionArguments](
    config: Expr[ArgBuilderConfig[Source, Dest, ArgSelector]]
  )(using Quotes, Fields.Source, Fields.Dest): Product =
    config match {
      case '{
            type argSelector <: FunctionArguments
            Arg.const[source, dest, argType, actualType, `argSelector`]($selector, $const)(using $ev1, $ev2)
          } =>
        val argName = Selectors.argName(Fields.dest, selector)
        Product.Const(argName, const)

      case '{
            type argSelector <: FunctionArguments
            Arg.computed[source, dest, argType, actualType, `argSelector`]($selector, $function)(using $ev1, $ev2)
          } =>
        val argName = Selectors.argName(Fields.dest, selector)
        Product.Computed(argName, function.asInstanceOf[Expr[Any => Any]])

      case '{
            type argSelector <: FunctionArguments
            Arg.renamed[source, dest, argType, fieldType, `argSelector`]($destSelector, $sourceSelector)(using $ev1, $ev2)
          } =>
        val argName = Selectors.argName(Fields.dest, destSelector)
        val fieldName = Selectors.fieldName(Fields.source, sourceSelector)
        Product.Renamed(argName, fieldName)

      case other => Failure.abort(Failure.UnsupportedConfig(other, Failure.ConfigType.Arg))
    }

  private def accessField(using Quotes)(value: quotes.reflect.Term, field: Field) = {
    import quotes.reflect.*
    Select.unique(value, field.name).asExpr
  }

}
