package io.github.arainko.ducktape.internal.macros

import scala.quoted.*
import io.github.arainko.ducktape.internal.modules.*
import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.function.*
import scala.deriving.*

private[ducktape] class ProductTransformerMacros(using val quotes: Quotes)
    extends Module,
      FieldModule,
      MirrorModule,
      SelectorModule,
      ConfigurationModule {
  import quotes.reflect.*
  import MaterializedConfiguration.*

  def via[A: Type, B: Type, Func](
    sourceValue: Expr[A],
    function: Expr[Func],
    Func: Expr[FunctionMirror.Aux[Func, B]],
    A: Expr[Mirror.ProductOf[A]]
  ): Expr[B] = function.asTerm match {
    case func @ SelectorLambda(vals, body) =>
      val functionFields = vals.map(Field.fromValDef)
      val sourceFields = Field.fromMirror(A).map(field => field.name -> field).toMap
      val calls = fieldTransformers(sourceValue, functionFields, sourceFields).map(_.value)

      Select.unique(func, "apply").appliedToArgs(calls).asExprOf[B]
    case other => report.errorAndAbort(s"'via' is only supported on eta-expanded methods!")
  }

  def viaConfigured[A: Type, B: Type, Func: Type, NamedArgs <: Tuple: Type](
    sourceValue: Expr[A],
    function: Expr[Func],
    config: Expr[Seq[ArgBuilderConfig[A, B, NamedArgs]]],
    A: Expr[Mirror.ProductOf[A]]
  ): Expr[B] = {
    val materializedConfig = config match {
      case Varargs(config) => MaterializedConfiguration.materializeArgConfig(config)
      case other => report.errorAndAbort("Failed to materialize ArgConfig!")
    }

    val functionFields = Field.fromNamedArguments[NamedArgs].map(field => field.name -> field).toMap
    val sourceFields = Field.fromMirror(A).map(field => field.name -> field).toMap

    val nonConfiguredFields = functionFields -- materializedConfig.map(_.destFieldName)
    val transformedFields =
      fieldTransformers(sourceValue, nonConfiguredFields.values.toList, sourceFields)
        .map(field => field.name -> field)
        .toMap

    val configuredFields =
      fieldConfigurations[A, B](
        materializedConfig,
        sourceValue,
        functionFields
      ).map(field => field.name -> field).toMap

    val callsInOrder = functionFields.map { (name, field) =>
      transformedFields
        .get(name)
        .getOrElse(configuredFields(name))
        .value
    }

    Select
      .unique(function.asTerm, "apply")
      .appliedToArgs(callsInOrder.toList)
      .asExprOf[B]
  }

  def transformConfigured[A: Type, B: Type, Config <: Tuple: Type](
    sourceValue: Expr[A],
    config: Expr[Seq[BuilderConfig[A, B]]],
    A: Expr[Mirror.ProductOf[A]],
    B: Expr[Mirror.ProductOf[B]]
  ): Expr[B] = {
    val materializedConfig = config match {
      case Varargs(config) => MaterializedConfiguration.materializeProductConfig(config)
      case other           => report.errorAndAbort(s"Failed to materialize field config: ${other.show} ")
    }

    val destinationFields = Field.fromMirror(B).map(field => field.name -> field).toMap
    val sourceFields = Field.fromMirror(A).map(field => field.name -> field).toMap

    val nonConfiguredFields = destinationFields -- materializedConfig.map(_.destFieldName)
    val transformedFields = fieldTransformers(sourceValue, nonConfiguredFields.values.toList, sourceFields)
    val configuredFields = fieldConfigurations[A, B](materializedConfig, sourceValue, destinationFields)

    constructor(TypeRepr.of[B])
      .appliedToArgs(transformedFields ++ configuredFields)
      .asExprOf[B]
  }

  def transform[A: Type, B: Type](
    sourceValue: Expr[A],
    A: Expr[Mirror.ProductOf[A]],
    B: Expr[Mirror.ProductOf[B]]
  ): Expr[B] = {
    val destinationFields = Field.fromMirror(B)
    val sourceFields = Field.fromMirror(A).map(field => field.name -> field).toMap
    val transformerFields = fieldTransformers(sourceValue, destinationFields, sourceFields)

    constructor(TypeRepr.of[B])
      .appliedToArgs(transformerFields.toList)
      .asExprOf[B]
  }

  private def fieldTransformers[A: Type](
    sourceValue: Expr[A],
    destinationFields: List[Field],
    sourceFieldMapping: Map[String, Field]
  ) =
    destinationFields.map { field =>
      field ->
        sourceFieldMapping
          .get(field.name)
          .getOrElse(report.errorAndAbort(s"No field named '${field.name}' found in ${TypeRepr.of[A].show}"))
    }.map { (dest, source) =>
      val call = resolveTransformer(sourceValue, source, dest)
      NamedArg(dest.name, call)
    }

  private def fieldConfigurations[A: Type, B: Type](
    config: List[MaterializedConfiguration.Product],
    sourceValue: Expr[A],
    destinationFieldMapping: Map[String, Field]
  ) = config
    .map(cfg => destinationFieldMapping(cfg.destFieldName) -> cfg)
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

  private def resolveTransformer[A: Type](sourceValue: Expr[A], source: Field, destination: Field) =
    source
      .transformerTo(destination)
      .map {
        case '{ $transformer: Transformer.Identity[source] } => accessField(sourceValue, source.name)
        case '{ $transformer: Transformer[source, dest] } =>
          val field = accessField(sourceValue, source.name).asExprOf[source]
          '{ $transformer.transform($field) }.asTerm
      }
      .getOrElse(report.errorAndAbort(s"Transformer[${source.tpe.show}, ${destination.tpe.show}] not found"))

  private def accessField[A: Type](value: Expr[A], fieldName: String) = Select.unique(value.asTerm, fieldName)

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
  inline def transform[A, B](source: A)(using
    A: Mirror.ProductOf[A],
    B: Mirror.ProductOf[B]
  ): B = ${ transformMacro[A, B]('source, 'A, 'B) }

  def transformMacro[A: Type, B: Type](
    source: Expr[A],
    A: Expr[Mirror.ProductOf[A]],
    B: Expr[Mirror.ProductOf[B]]
  )(using Quotes): Expr[B] = ProductTransformerMacros().transform(source, A, B)

  inline def via[A, B, Func](source: A, inline function: Func)(using
    A: Mirror.ProductOf[A],
    Func: FunctionMirror.Aux[Func, B]
  ): B = ${ viaMacro('source, 'function, 'Func, 'A) }

  def viaMacro[A: Type, B: Type, Func](
    source: Expr[A],
    function: Expr[Func],
    Func: Expr[FunctionMirror.Aux[Func, B]],
    A: Expr[Mirror.ProductOf[A]]
  )(using Quotes) =
    ProductTransformerMacros().via(source, function, Func, A)

  inline def viaWithBuilder[A, B, Func, NamedArgs <: Tuple](
    source: A,
    inline function: Func,
    inline config: ArgBuilderConfig[A, B, NamedArgs]*
  )(using
    A: Mirror.ProductOf[A]
  ): B = ${ viaWithBuilderMacro('source, 'function, 'config, 'A) }

  def viaWithBuilderMacro[A: Type, B: Type, Func: Type, NamedArgs <: Tuple: Type](
    sourceValue: Expr[A],
    function: Expr[Func],
    config: Expr[Seq[ArgBuilderConfig[A, B, NamedArgs]]],
    A: Expr[Mirror.ProductOf[A]]
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


  // inline def transformWhateverConfigured[Source, Dest](sourceValue: Source, inline config: BuilderConfig[Source, Dest]*) =
    //  ${ transformWhateverConfiguredMacro('sourceValue, 'config) }
  
     //TODO: Moove that into `TransformerMacros` and for the love of god name it somewhat else
  // def transformWhateverConfiguredMacro[Source: Type, Dest: Type](
  //   sourceValue: Expr[Source],
  //   config: Expr[Seq[BuilderConfig[Source, Dest]]]
  // )(using Quotes) = {
  //   import quotes.reflect.*
  //   val sourceMirror = Expr.summon[DerivingMirror.Of[Source]]
  //   val destMirror = Expr.summon[DerivingMirror.Of[Dest]]

  //   sourceMirror.zip(destMirror).collect {
  //     case '{ $source: Expr[Mirror.ProductOf[Source]} -> '{ $dest: Expr[Mirror.ProductOf[Dest]]] } =>
  //       ProductTransformerMacros.transformConfiguredMacro(sourceValue, config, source, dest)
  //     case '{ $source: DerivingMirror.SumOf[Source] } -> '{ $dest: DerivingMirror.SumOf[Dest] } =>
  //       CoproductTransformerMacros.transformConfiguredMacro(sourceValue, config, source, dest)
  //   }.getOrElse(report.errorAndAbort("BARF"))
  // }

}
