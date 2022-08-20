package io.github.arainko.ducktape.internal.macros

import scala.quoted.*
import io.github.arainko.ducktape.internal.modules.*
import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.function.*
import scala.deriving.*

private[ducktape] class ProductTransformerMacros(using val quotes: Quotes)
    extends Module,
      FieldModule,
      CaseModule,
      MirrorModule,
      SelectorModule,
      ConfigurationModule {
  import quotes.reflect.*
  import MaterializedConfiguration.*

  def via[Source: Type, Dest: Type, Func](
    sourceValue: Expr[Source],
    function: Expr[Func],
    Func: Expr[FunctionMirror.Aux[Func, Dest]],
    Source: Expr[Mirror.ProductOf[Source]]
  ): Expr[Dest] = function.asTerm match {
    case func @ FunctionLambda(vals, _) =>
      given Fields.Source = Fields.Source.fromMirror(Source)
      given Fields.Dest = Fields.Dest.fromValDefs(vals)

      val calls = fieldTransformers(sourceValue, Fields.dest.value).map(_.value)
      Select.unique(func, "apply").appliedToArgs(calls).asExprOf[Dest]
    case other => report.errorAndAbort(s"'via' is only supported on eta-expanded methods!")
  }

  def viaConfigured[Source: Type, Dest: Type, Func: Type, NamedArgs <: Tuple: Type](
    sourceValue: Expr[Source],
    function: Expr[Func],
    config: Expr[Seq[ArgBuilderConfig[Source, Dest, NamedArgs]]],
    Source: Expr[Mirror.ProductOf[Source]]
  ): Expr[Dest] = {
    given Fields.Source = Fields.Source.fromMirror(Source)
    given Fields.Dest = Fields.Dest.fromNamedArguments[NamedArgs]
    val materializedConfig = MaterializedConfiguration.materializeArgConfig(config)
    val nonConfiguredFields = Fields.dest.byName -- materializedConfig.map(_.destFieldName)

    val transformedFields =
      fieldTransformers(sourceValue, nonConfiguredFields.values.toList)
        .map(field => field.name -> field)
        .toMap

    val configuredFields =
      fieldConfigurations(
        materializedConfig,
        sourceValue
      ).map(field => field.name -> field).toMap

    val callsInOrder = Fields.dest.byName.map { (name, field) =>
      transformedFields
        .get(name)
        .getOrElse(configuredFields(name))
        .value
    }

    Select
      .unique(function.asTerm, "apply")
      .appliedToArgs(callsInOrder.toList)
      .asExprOf[Dest]
  }

  def transformConfigured[Source: Type, Dest: Type](
    sourceValue: Expr[Source],
    config: Expr[Seq[BuilderConfig[Source, Dest]]],
    Source: Expr[Mirror.ProductOf[Source]],
    Dest: Expr[Mirror.ProductOf[Dest]]
  ): Expr[Dest] = {
    given Fields.Source = Fields.Source.fromMirror(Source)
    given Fields.Dest = Fields.Dest.fromMirror(Dest)

    val materializedConfig = MaterializedConfiguration.materializeProductConfig(config)
    val nonConfiguredFields = Fields.dest.byName -- materializedConfig.map(_.destFieldName)
    val transformedFields = fieldTransformers(sourceValue, nonConfiguredFields.values.toList)
    val configuredFields = fieldConfigurations(materializedConfig, sourceValue)

    constructor(TypeRepr.of[Dest])
      .appliedToArgs(transformedFields ++ configuredFields)
      .asExprOf[Dest]
  }

  def transform[Source: Type, Dest: Type](
    sourceValue: Expr[Source],
    Source: Expr[Mirror.ProductOf[Source]],
    Dest: Expr[Mirror.ProductOf[Dest]]
  ): Expr[Dest] = {
    given Fields.Source = Fields.Source.fromMirror(Source)
    given Fields.Dest = Fields.Dest.fromMirror(Dest)
    val transformerFields = fieldTransformers(sourceValue, Fields.dest.value)

    constructor(TypeRepr.of[Dest])
      .appliedToArgs(transformerFields.toList)
      .asExprOf[Dest]
  }

  def transformFromAnyVal[Source <: AnyVal: Type, Dest: Type](
    sourceValue: Expr[Source]
  ): Expr[Dest] = {
    val tpe = TypeRepr.of[Source]
    val fieldSymbol =
      tpe.typeSymbol.fieldMembers.headOption
        .getOrElse(report.errorAndAbort(s"Failed to fetch the wrapped field name of ${tpe.show}"))

    accessField(sourceValue, fieldSymbol.name).asExprOf[Dest]
  }

  def transformToAnyVal[Source: Type, Dest <: AnyVal: Type](
    sourceValue: Expr[Source]
  ): Expr[Dest] =
    constructor(TypeRepr.of[Dest])
      .appliedTo(sourceValue.asTerm)
      .asExprOf[Dest]

  private def fieldTransformers[Source: Type](
    sourceValue: Expr[Source],
    fieldsToTransformInto: List[Field]
  )(using Fields.Source) =
    fieldsToTransformInto.map { field =>
      field ->
        Fields.source
          .get(field.name)
          .getOrElse(abort(Failure.NoFieldMapping(field.name, TypeRepr.of[Source])))
    }.map { (dest, source) =>
      val call = resolveTransformer(sourceValue, source, dest)

      NamedArg(dest.name, call)
    }

  private def fieldConfigurations[Source: Type](
    config: List[MaterializedConfiguration.Product],
    sourceValue: Expr[Source]
  )(using Fields.Dest) =
    config
      .map(cfg => Fields.dest.unsafeGet(cfg.destFieldName) -> cfg)
      .map { (field, cfg) =>
        val call = cfg match {
          case Product.Const(label, value)       => value
          case Product.Computed(label, function) => '{ $function($sourceValue) }
          case Product.Renamed(dest, source)     => accessField(sourceValue, source).asExpr
        }

        val castedCall = field.tpe.asType match {
          case '[fieldTpe] => call.asExprOf[fieldTpe]
        }

        NamedArg(field.name, castedCall.asTerm)
      }

  private def resolveTransformer[Source: Type](sourceValue: Expr[Source], source: Field, destination: Field) =
    source.transformerTo(destination) match {
      case '{ $transformer: Transformer.Identity[source] } => accessField(sourceValue, source.name)
      case '{ $transformer: Transformer[source, dest] } =>
        val field = accessField(sourceValue, source.name).asExprOf[source]
        '{ $transformer.transform($field) }.asTerm
    }

  private def accessField[Source: Type](value: Expr[Source], fieldName: String) = Select.unique(value.asTerm, fieldName)

  private def constructor(tpe: TypeRepr): Term = {
    val (repr, constructor, tpeArgs) = tpe match {
      case AppliedType(repr, reprArguments) => (repr, repr.typeSymbol.primaryConstructor, reprArguments)
      case notApplied                       => (tpe, tpe.typeSymbol.primaryConstructor, Nil)
    }

    New(Inferred(repr))
      .select(constructor)
      .appliedToTypes(tpeArgs)
  }

}

private[ducktape] object ProductTransformerMacros {
  inline def transform[Source, Dest](source: Source)(using
    Source: Mirror.ProductOf[Source],
    Dest: Mirror.ProductOf[Dest]
  ): Dest = ${ transformMacro[Source, Dest]('source, 'Source, 'Dest) }

  def transformMacro[Source: Type, Dest: Type](
    source: Expr[Source],
    Source: Expr[Mirror.ProductOf[Source]],
    Dest: Expr[Mirror.ProductOf[Dest]]
  )(using Quotes): Expr[Dest] = ProductTransformerMacros().transform(source, Source, Dest)

  inline def via[Source, Dest, Func](source: Source, inline function: Func)(using
    Source: Mirror.ProductOf[Source],
    Func: FunctionMirror.Aux[Func, Dest]
  ): Dest = ${ viaMacro('source, 'function, 'Func, 'Source) }

  def viaMacro[Source: Type, Dest: Type, Func](
    source: Expr[Source],
    function: Expr[Func],
    Func: Expr[FunctionMirror.Aux[Func, Dest]],
    Source: Expr[Mirror.ProductOf[Source]]
  )(using Quotes) =
    ProductTransformerMacros().via(source, function, Func, Source)

  inline def viaConfigured[Source, Dest, Func, NamedArgs <: Tuple](
    source: Source,
    inline function: Func,
    inline config: ArgBuilderConfig[Source, Dest, NamedArgs]*
  )(using Source: Mirror.ProductOf[Source]): Dest =
    ${ viaConfiguredMacro('source, 'function, 'config, 'Source) }

  def viaConfiguredMacro[Source: Type, Dest: Type, Func: Type, NamedArgs <: Tuple: Type](
    sourceValue: Expr[Source],
    function: Expr[Func],
    config: Expr[Seq[ArgBuilderConfig[Source, Dest, NamedArgs]]],
    A: Expr[Mirror.ProductOf[Source]]
  )(using Quotes) =
    ProductTransformerMacros().viaConfigured(sourceValue, function, config, A)

  inline def transformConfigured[Source, Dest](sourceValue: Source, inline config: BuilderConfig[Source, Dest]*)(using
    Source: Mirror.ProductOf[Source],
    Dest: Mirror.ProductOf[Dest]
  ) = ${ transformConfiguredMacro('sourceValue, 'config, 'Source, 'Dest) }

  def transformConfiguredMacro[Source: Type, Dest: Type](
    sourceValue: Expr[Source],
    config: Expr[Seq[BuilderConfig[Source, Dest]]],
    Source: Expr[Mirror.ProductOf[Source]],
    Dest: Expr[Mirror.ProductOf[Dest]]
  )(using Quotes) =
    ProductTransformerMacros().transformConfigured(sourceValue, config, Source, Dest)

  inline def transformFromAnyVal[Source <: AnyVal, Dest](sourceValue: Source): Dest =
    ${ transformFromAnyValMacro[Source, Dest]('sourceValue) }

  def transformFromAnyValMacro[Source <: AnyVal: Type, Dest: Type](sourceValue: Expr[Source])(using Quotes): Expr[Dest] =
    ProductTransformerMacros().transformFromAnyVal[Source, Dest](sourceValue)

  inline def transfromToAnyVal[Source, Dest <: AnyVal](sourceValue: Source) =
    ${ transformToAnyValMacro[Source, Dest]('sourceValue) }

  def transformToAnyValMacro[Source: Type, Dest <: AnyVal: Type](sourceValue: Expr[Source])(using Quotes): Expr[Dest] =
    ProductTransformerMacros().transformToAnyVal[Source, Dest](sourceValue)

}
