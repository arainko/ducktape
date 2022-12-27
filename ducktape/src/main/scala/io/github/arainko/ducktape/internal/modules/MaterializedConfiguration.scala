package io.github.arainko.ducktape.internal.modules

import io.github.arainko.ducktape.function.FunctionArguments
import io.github.arainko.ducktape.{ Case => CaseConfig, Field => FieldConfig, * }

import scala.quoted.*

private[ducktape] sealed trait MaterializedConfiguration {
  val pos: Pos
}

private[ducktape] object MaterializedConfiguration {
  enum Product extends MaterializedConfiguration {
    val destFieldName: String

    case Const(destFieldName: String, value: Expr[Any])(val pos: Pos)
    case Computed(destFieldName: String, fuction: Expr[Any => Any])(val pos: Pos)
    case Renamed(destFieldName: String, sourceFieldName: String)(val pos: Pos)
  }

  object Product {
    def fromFieldConfig[Source, Dest](
      config: Expr[Seq[BuilderConfig[Source, Dest]]]
    )(using Quotes, Fields.Source, Fields.Dest): List[Product] =
      parseConfig(Failure.ConfigType.Field, config)(_.flatMap(materializeSingleProductConfig), _.destFieldName)

    def fromArgConfig[Source, Dest, ArgSelector <: FunctionArguments](
      config: Expr[Seq[ArgBuilderConfig[Source, Dest, ArgSelector]]]
    )(using Quotes, Fields.Source, Fields.Dest): List[Product] =
      parseConfig(Failure.ConfigType.Arg, config)(_.map(materializeSingleArgConfig), _.destFieldName)

    private def materializeSingleProductConfig[Source, Dest](
      config: Expr[BuilderConfig[Source, Dest]]
    )(using Quotes, Fields.Source, Fields.Dest) = {
      import quotes.reflect.*

      config match {
        case '{
              FieldConfig.const[source, dest, fieldType, actualType](
                $selector,
                $value
              )(using $ev1, $ev2, $ev3)
            } =>
          val name = Selectors.fieldName(Fields.dest, selector)
          Product.Const(name, value)(Pos.fromExpr(config)) :: Nil

        case '{
              FieldConfig.computed[source, dest, fieldType, actualType](
                $selector,
                $function
              )(using $ev1, $ev2, $ev3)
            } =>
          val name = Selectors.fieldName(Fields.dest, selector)
          Product.Computed(name, function.asInstanceOf[Expr[Any => Any]])(Pos.fromExpr(config)) :: Nil

        case '{
              FieldConfig.renamed[source, dest, sourceFieldType, destFieldType](
                $destSelector,
                $sourceSelector
              )(using $ev1, $ev2, $ev3)
            } =>
          val destFieldName = Selectors.fieldName(Fields.dest, destSelector)
          val sourceFieldName = Selectors.fieldName(Fields.source, sourceSelector)
          Product.Renamed(destFieldName, sourceFieldName)(Pos.fromExpr(config)) :: Nil

        case '{ FieldConfig.default[source, dest, destFieldType]($destSelector)(using $ev1, $ev2) } =>
          val destFieldName = Selectors.fieldName(Fields.dest, destSelector)
          val field = Fields.dest.unsafeGet(destFieldName)
          val default = field.default.getOrElse(Failure.emit(Failure.DefaultMissing(field.name, Type.of[dest], config)))

          Failure.cond(
            successCondition = default.asTerm.tpe <:< TypeRepr.of(using field.tpe),
            value = Product.Const(field.name, default)(Pos.fromExpr(config)) :: Nil,
            failure = Failure.InvalidDefaultType(field, Type.of[dest], config)
          )

        case config @ '{
              FieldConfig.allMatching[source, dest, fieldSource]($fieldSource)(using $ev1, $ev2, $fieldSourceMirror)
            } =>
          val fieldSourceFields = Fields.Source.fromMirror(fieldSourceMirror)
          val fieldSourceTerm = fieldSource.asTerm
          val materializedConfig =
            fieldSourceFields.value.flatMap { sourceField =>
              Fields.dest.byName
                .get(sourceField.name)
                .filter(sourceField <:< _)
                .map(field => Product.Const(field.name, accessField(fieldSourceTerm, field))(Pos.fromExpr(config)))
            }

          Failure.cond(
            successCondition = materializedConfig.nonEmpty,
            value = materializedConfig,
            failure = Failure.FieldSourceMatchesNoneOfDestFields(config, summon[Type[fieldSource]], summon[Type[dest]])
          )
        case other => Failure.emit(Failure.UnsupportedConfig(other, Failure.ConfigType.Field))
      }
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
          Product.Const(argName, const)(quotes.reflect.asTerm(config).pos)

        case '{
              type argSelector <: FunctionArguments
              Arg.computed[source, dest, argType, actualType, `argSelector`]($selector, $function)(using $ev1, $ev2)
            } =>
          val argName = Selectors.argName(Fields.dest, selector)
          Product.Computed(argName, function.asInstanceOf[Expr[Any => Any]])(Pos.fromExpr(config))

        case '{
              type argSelector <: FunctionArguments
              Arg.renamed[source, dest, argType, fieldType, `argSelector`]($destSelector, $sourceSelector)(using $ev1, $ev2)
            } =>
          val argName = Selectors.argName(Fields.dest, destSelector)
          val fieldName = Selectors.fieldName(Fields.source, sourceSelector)
          Product.Renamed(argName, fieldName)(Pos.fromExpr(config))

        case other => Failure.emit(Failure.UnsupportedConfig(other, Failure.ConfigType.Arg))
      }
  }

  enum FallibleProduct[F[+x]](val destFieldName: String) extends MaterializedConfiguration {

    case Const(private val fieldName: String, value: Expr[F[Any]]) extends FallibleProduct[F](fieldName)

    case Computed(private val fieldName: String, function: Expr[Any => F[Any]]) extends FallibleProduct[F](fieldName)

    case Total(value: Product) extends FallibleProduct[F](value.destFieldName)
  }

  object FallibleProduct {

  }

  enum Coproduct extends MaterializedConfiguration {
    val tpe: Type[?]

    case Computed(tpe: Type[?], function: Expr[Any => Any])(val pos: Pos)
    case Const(tpe: Type[?], value: Expr[Any])(val pos: Pos)
  }

  object Coproduct {
    def fromCaseConfig[Source, Dest](
      config: Expr[Seq[BuilderConfig[Source, Dest]]]
    )(using Quotes, Cases.Source, Cases.Dest): List[Coproduct] =
      parseConfig(Failure.ConfigType.Case, config)(_.map(materializeSingleCoproductConfig), _.tpe.fullName)

    private def materializeSingleCoproductConfig[Source, Dest](config: Expr[BuilderConfig[Source, Dest]])(using Quotes) =
      config match {
        case '{ CaseConfig.computed[sourceSubtype].apply[source, dest]($function)(using $ev1, $ev2, $ev3) } =>
          Coproduct.Computed(summon[Type[sourceSubtype]], function.asInstanceOf[Expr[Any => Any]])(Pos.fromExpr(config))

        case '{ CaseConfig.const[sourceSubtype].apply[source, dest]($value)(using $ev1, $ev2, $ev3) } =>
          Coproduct.Const(summon[Type[sourceSubtype]], value)(Pos.fromExpr(config))

        case other => Failure.emit(Failure.UnsupportedConfig(other, Failure.ConfigType.Case))
      }
  }

  private def parseConfig[Config, MaterializedConfig <: MaterializedConfiguration](
    configType: Failure.ConfigType,
    config: Expr[Seq[Config]]
  )(
    extractor: Seq[Expr[Config]] => Seq[MaterializedConfig],
    discriminator: MaterializedConfig => String
  )(using Quotes) = {
    import quotes.reflect.*

    extractor(Varargs.unapply(config).getOrElse(Failure.emit(Failure.UnsupportedConfig(config, configType))))
      .groupBy(discriminator)
      .map { (name, configs) =>
        if (configs.sizeIs > 1) configs.foreach(cfg => Warning.emit(Warning.ConfiguredRepeatedly(cfg, configType)))

        configs.last // keep the last applied field config only
      }
      .toList
  }

  private def materializeSingleFallibleProductConfig[F[+x]: Type, Source: Type, Dest: Type](
    config: Expr[FallibleBuilderConfig[F, Source, Dest] | BuilderConfig[Source, Dest]]
  )(using Quotes, Fields.Source, Fields.Dest): List[FallibleProduct[F]] =
    config match {
      case '{
            FieldConfig.fallibleConst[F, source, dest, fieldType, actualType](
              $selector,
              $value
            )(using $ev1, $ev2, $ev3)
          } =>
        val name = Selectors.fieldName(Fields.dest, selector)
        FallibleProduct.Const(name, value) :: Nil

      case '{
            FieldConfig.fallibleComputed[F, source, dest, fieldType, actualType](
              $selector,
              $function
            )(using $ev1, $ev2, $ev3)
          } =>
        val name = Selectors.fieldName(Fields.dest, selector)
        FallibleProduct.Computed(name, function.asInstanceOf[Expr[Any => F[Any]]]) :: Nil

      case '{ $config: BuilderConfig[Source, Dest] } =>
        materializeSingleProductConfig(config).map(FallibleProduct.Total(_))

      // TODO: Add more suggestions to this failure
      case other =>
        Failure.abort(Failure.UnsupportedConfig(other, Failure.ConfigType.Field))
    }

  private def materializeSingleFallibleArgConfig[F[+x]: Type, Source: Type, Dest: Type, ArgSelector <: FunctionArguments: Type](
    config: Expr[FallibleArgBuilderConfig[F, Source, Dest, ArgSelector] | ArgBuilderConfig[Source, Dest, ArgSelector]]
  )(using Quotes, Fields.Source, Fields.Dest): FallibleProduct[F] =
    config match {
      case '{
            type argSelector <: FunctionArguments
            Arg.fallibleConst[F, source, dest, argType, actualType, `argSelector`]($selector, $const)(using $ev1, $ev2)
          } =>
        val argName = Selectors.argName(Fields.dest, selector)
        FallibleProduct.Const(argName, const)

      case '{
            type argSelector <: FunctionArguments
            Arg.fallibleComputed[F, source, dest, argType, actualType, `argSelector`]($selector, $function)(using $ev1, $ev2)
          } =>
        val argName = Selectors.argName(Fields.dest, selector)
        FallibleProduct.Computed(argName, function.asInstanceOf[Expr[Any => F[Any]]])

      case '{ $config: ArgBuilderConfig[Source, Dest, ArgSelector] } => FallibleProduct.Total(materializeSingleArgConfig(config))

      case other => Failure.abort(Failure.UnsupportedConfig(other, Failure.ConfigType.Arg))
    }

  private def accessField(using Quotes)(value: quotes.reflect.Term, field: Field) = {
    import quotes.reflect.*
    Select.unique(value, field.name).asExpr
  }

}
