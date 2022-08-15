package io.github.arainko.ducktape.internal.modules

import io.github.arainko.ducktape.{ Case => CaseConfig, Field => FieldConfig, _ }

import scala.quoted.*

private[internal] trait ConfigurationModule { self: Module & SelectorModule & MirrorModule & FieldModule & CaseModule =>
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

    def materializeProductConfig[Source, Dest](
      config: Expr[Seq[BuilderConfig[Source, Dest]]]
    )(using Fields.Source, Fields.Dest): List[Product] =
      Varargs
        .unapply(config)
        .getOrElse(abort(Failure.UnsupportedConfig(config, "field")))
        .map(materializeSingleProductConfig)
        .groupBy(_.destFieldName)
        .map((_, fieldConfigs) => fieldConfigs.last) // keep the last applied field config only
        .toList

    def materializeArgConfig[Source, Dest, NamedArguments <: Tuple](
      config: Expr[Seq[ArgBuilderConfig[Source, Dest, NamedArguments]]]
    )(using Fields.Source, Fields.Dest): List[Product] =
      Varargs
        .unapply(config)
        .getOrElse(abort(Failure.UnsupportedConfig(config, "arg")))
        .map(materializeSingleArgConfig)
        .groupBy(_.destFieldName)
        .map((_, fieldConfigs) => fieldConfigs.last) // keep the last applied field config only
        .toList

    def materializeCoproductConfig[Source, Dest](
      config: Expr[Seq[BuilderConfig[Source, Dest]]]
    )(using Cases.Source, Cases.Dest): List[Coproduct] =
      Varargs
        .unapply(config)
        .getOrElse(abort(Failure.UnsupportedConfig(config, "case")))
        .map(materializeSingleCoproductConfig)
        .groupBy(_.tpe.typeSymbol.fullName) // TODO: Ths is probably not the best way to do this (?)
        .map((_, fieldConfigs) => fieldConfigs.last) // keep the last applied field config only
        .toList

    private def materializeSingleProductConfig[Source, Dest](
      config: Expr[BuilderConfig[Source, Dest]]
    )(using Fields.Source, Fields.Dest) =
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

        case other => abort(Failure.UnsupportedConfig(other, "field"))
      }

    private def materializeSingleCoproductConfig[Source, Dest](config: Expr[BuilderConfig[Source, Dest]]) =
      config match {
        case '{ CaseConfig.computed[sourceSubtype].apply[source, dest]($function)(using $ev1, $ev2, $ev3) } =>
          Coproduct.Computed(TypeRepr.of[sourceSubtype], function.asInstanceOf[Expr[Any => Any]])

        case '{ CaseConfig.const[sourceSubtype].apply[source, dest]($value)(using $ev1, $ev2, $ev3) } =>
          Coproduct.Const(TypeRepr.of[sourceSubtype], value)

        case other => abort(Failure.UnsupportedConfig(other, "case"))
      }

    /**
     * We check arg types here because if an arg is not found `FunctionArguments.FindByName` returns a Nothing
     * which trips up evidence summoning which in turn only tells us that an arg was Nothing (with no suggestions etc.)
     *
     * TODO: See if it works properly, if not we may need to go back ot the old encoding with the evidence and bad error messages
     */
    private def materializeSingleArgConfig[Source, Dest, NamedArguments <: Tuple](
      config: Expr[ArgBuilderConfig[Source, Dest, NamedArguments]]
    )(using Fields.Source, Fields.Dest): Product =
      config match {
        case '{
              type namedArgs <: Tuple
              Arg.const[source, dest, argType, actualType, `namedArgs`]($selector, $const)(using $ev1)
            } =>
          val argName = Selectors.argName(Fields.dest, selector)
          verifyArgSelectorTypes(selector, argName, const, TypeRepr.of[argType], TypeRepr.of[actualType])
          Product.Const(argName, const)

        case '{
              type namedArgs <: Tuple
              Arg.computed[source, dest, argType, actualType, `namedArgs`]($selector, $function)(using $ev1)
            } =>
          val argName = Selectors.argName(Fields.dest, selector)
          verifyArgSelectorTypes(selector, argName, function, TypeRepr.of[argType], TypeRepr.of[actualType])
          Product.Computed(argName, function.asInstanceOf[Expr[Any => Any]])

        case '{
              type namedArgs <: Tuple
              Arg.renamed[source, dest, argType, fieldType, `namedArgs`]($destSelector, $sourceSelector)(using $ev1)
            } =>
          val argName = Selectors.argName(Fields.dest, destSelector)
          val fieldName = Selectors.fieldName(Fields.source, sourceSelector)
          verifyArgSelectorTypes(destSelector, argName, sourceSelector, TypeRepr.of[argType], TypeRepr.of[fieldType])
          Product.Renamed(argName, fieldName)

        case other => abort(Failure.UnsupportedConfig(other, "arg"))
      }

    private def verifyArgSelectorTypes(
      selector: Expr[Any],
      argName: String,
      mismatchedValue: Expr[Any],
      expected: TypeRepr,
      actual: TypeRepr
    ) = if (!(actual <:< expected))
      abort(Failure.InvalidArgSelector.TypeMismatch(selector, argName, expected, actual, mismatchedValue))

  }
}
