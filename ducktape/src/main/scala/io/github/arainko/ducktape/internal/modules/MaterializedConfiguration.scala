package io.github.arainko.ducktape.internal.modules

import scala.quoted.*
import io.github.arainko.ducktape.{ Field as FieldConfig, Case as CaseConfig, * }
import io.github.arainko.ducktape.function.FunctionArguments

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
      .map(materializeSingleProductConfig)
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
  )(using Quotes, Fields.Source, Fields.Dest) =
    config match {
      case '{
            FieldConfig.const[source, dest, fieldType, actualType](
              $selector,
              $value
            )(using $ev1, $ev2, $ev3)
          } =>
        val name = Selectors.fieldName(Fields.dest, selector)
        Product.Const(name, value)

      case '{
            FieldConfig.computed[source, dest, fieldType, actualType](
              $selector,
              $function
            )(using $ev1, $ev2, $ev3)
          } =>
        val name = Selectors.fieldName(Fields.dest, selector)
        Product.Computed(name, function.asInstanceOf[Expr[Any => Any]])

      case '{
            FieldConfig.renamed[source, dest, sourceFieldType, destFieldType](
              $destSelector,
              $sourceSelector
            )(using $ev1, $ev2, $ev3)
          } =>
        val destFieldName = Selectors.fieldName(Fields.dest, destSelector)
        val sourceFieldName = Selectors.fieldName(Fields.source, sourceSelector)
        Product.Renamed(destFieldName, sourceFieldName)

      case other => Failure.abort(Failure.UnsupportedConfig(other, Failure.ConfigType.Field))
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

}
