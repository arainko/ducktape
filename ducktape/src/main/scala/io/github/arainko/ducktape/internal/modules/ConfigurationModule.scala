package io.github.arainko.ducktape.internal.modules

import io.github.arainko.ducktape.{ Case => CaseConfig, Field => FieldConfig, _ }

import scala.quoted.*

private[internal] trait ConfigurationModule { self: Module & SelectorModule & MirrorModule & FieldModule =>
  import quotes.reflect.*

  sealed trait MaterializedConfiguration

  object MaterializedConfiguration {
    enum Product extends MaterializedConfiguration {
      val destFieldName: String

      case Const(destFieldName: String, value: Expr[Any])
      case Computed(destFieldName: String, fuction: Expr[Any => Any])
      case Renamed(destFieldName: String, sourceFieldName: String)
    }

    enum Coproduct extends MaterializedConfiguration {
      val tpe: TypeRepr

      case Computed(tpe: TypeRepr, function: Expr[Any => Any])
      case Const(tpe: TypeRepr, value: Expr[Any])
    }

    def materializeProductConfig[Source, Dest](config: Seq[Expr[BuilderConfig[Source, Dest]]]): List[Product] =
      config
        .map(materializeSingleProductConfig)
        .groupBy(_.destFieldName)
        .map((_, fieldConfigs) => fieldConfigs.last) // keep the last applied field config only
        .toList

    def materializeArgConfig[Source, Dest, NamedArguments <: Tuple](
      config: Seq[Expr[ArgBuilderConfig[Source, Dest, NamedArguments]]]
    ): List[Product] =
      config
        .map(materializeSingleArgConfig)
        .groupBy(_.destFieldName)
        .map((_, fieldConfigs) => fieldConfigs.last) // keep the last applied field config only
        .toList

    def materializeCoproductConfig[Source, Dest](config: Seq[Expr[BuilderConfig[Source, Dest]]]): List[Coproduct] =
      config
        .map(materializeSingleCoproductConfig)
        .groupBy(_.tpe.typeSymbol.fullName) // TODO: Ths is probably not the best way to do this (?)
        .map((_, fieldConfigs) => fieldConfigs.last) // keep the last applied field config only
        .toList

    private def materializeSingleProductConfig[Source, Dest](config: Expr[BuilderConfig[Source, Dest]]) =
      config match {
        case '{
              FieldConfig.const[source, dest, fieldType, actualType](
                ${ FieldSelector(name) },
                $value
              )(using $ev1, $ev2, $ev3)
            } =>
          Product.Const(name, value)

        case '{
              FieldConfig.computed[source, dest, fieldType, actualType](
                ${ FieldSelector(name) },
                $function
              )(using $ev1, $ev2, $ev3)
            } =>
          Product.Computed(name, function.asInstanceOf[Expr[Any => Any]])

        case '{
              FieldConfig.renamed[source, dest, sourceFieldType, destFieldType](
                ${ FieldSelector(destFieldName) },
                ${ FieldSelector(sourceFieldName) }
              )(using $ev1, $ev2, $ev3)
            } =>
          Product.Renamed(destFieldName, sourceFieldName)

        case other => report.errorAndAbort(s"Unsupported field configuration: ${other.asTerm.show}")
      }

    private def materializeSingleCoproductConfig[Source, Dest](config: Expr[BuilderConfig[Source, Dest]]) =
      config match {
        case '{ CaseConfig.computed[sourceSubtype].apply[source, dest]($function)(using $ev1, $ev2, $ev3) } =>
          Coproduct.Computed(TypeRepr.of[sourceSubtype], function.asInstanceOf[Expr[Any => Any]])

        case '{ CaseConfig.const[sourceSubtype].apply[source, dest]($value)(using $ev1, $ev2, $ev3) } =>
          Coproduct.Const(TypeRepr.of[sourceSubtype], value)

        case other => report.errorAndAbort(s"Unsupported field configuration: ${other.asTerm.show}")
      }

    private def materializeSingleArgConfig[Source, Dest, NamedArguments <: Tuple](
      config: Expr[ArgBuilderConfig[Source, Dest, NamedArguments]]
    ) = config match {
      case '{
            type namedArgs <: Tuple
            Arg.const[source, dest, argType, actualType, `namedArgs`]($selector, $const)(using $ev1, $ev2)
          } =>
        val argName = selectedArg[`namedArgs`, argType](selector)
        Product.Const(argName, const)

      case '{
            type namedArgs <: Tuple
            Arg.computed[source, dest, argType, actualType, `namedArgs`]($selector, $function)(using $ev1, $ev2)
          } =>
        val argName = selectedArg[`namedArgs`, argType](selector)
        Product.Computed(argName, function.asInstanceOf[Expr[Any => Any]])

      case '{
            type namedArgs <: Tuple
            Arg.renamed[source, dest, argType, fieldType, `namedArgs`]($destSelector, $sourceSelector)(using $sourceMirror, $ev2)
          } =>
        val argName = selectedArg[`namedArgs`, argType](destSelector)
        val fieldName = selectedField[source, fieldType](sourceMirror, sourceSelector)
        Product.Renamed(argName, fieldName)

      case other => report.errorAndAbort(s"Unsupported field configuration: ${other.asTerm.show}")
    }

  }
}
