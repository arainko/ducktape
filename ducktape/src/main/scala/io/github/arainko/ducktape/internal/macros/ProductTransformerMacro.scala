package io.github.arainko.ducktape.internal.macros

import scala.quoted.*
import io.github.arainko.ducktape.Configuration.*
import io.github.arainko.ducktape.internal.modules.*
import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.function.*
import scala.deriving.Mirror as DerivingMirror

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
    Func: Expr[FunctionMirror.Aux[Func, ?, B]],
    A: DerivingMirror.ProductOf[A]
  ): Expr[B] = function.asTerm match {
    case func @ SelectorLambda(vals, body) =>
      val functionFields = vals.map(Field.fromValDef)
      val sourceFields = Field.fromMirror(A).map(field => field.name -> field).toMap
      val calls = fieldTransformers(sourceValue, functionFields, sourceFields).map(_.value)

      Select.unique(func, "apply").appliedToArgs(calls).asExprOf[B]
    case other => report.errorAndAbort(s"'via' is only supported on eta-expanded methods!")
  }

  def viaConfigured[A: Type, B: Type, Func: Type, NamedArgs <: Tuple: Type, Config <: Tuple: Type](
    sourceValue: Expr[A],
    builder: Expr[ViaBuilder[?, A, B, Func, NamedArgs, Config]],
    Func: Expr[FunctionMirror.Aux[Func, ?, B]],
    A: DerivingMirror.ProductOf[A]
  ): Expr[B] = {
    val config = materializeProductConfig[Config]
    val functionFields = Field.fromNamedArguments[NamedArgs].map(field => field.name -> field).toMap
    val sourceFields = Field.fromMirror(A).map(field => field.name -> field).toMap

    val nonConfiguredFields = functionFields -- config.map(_.name)
    val transformedFields =
      fieldTransformers(sourceValue, nonConfiguredFields.values.toList, sourceFields)
        .map(field => field.name -> field)
        .toMap

    val configuredFields =
      fieldConfigurations[A, B](
        config,
        sourceValue,
        '{ $builder.constants },
        '{ $builder.computeds },
        functionFields
      ).map(field => field.name -> field).toMap

    val callsInOrder = functionFields.map { (name, field) =>
      transformedFields
        .get(name)
        .getOrElse(configuredFields(name))
        .value
    }

    Select
      .unique('{ $builder.function }.asTerm, "apply")
      .appliedToArgs(callsInOrder.toList)
      .asExprOf[B]
  }

  def transformConfigured[A: Type, B: Type, Config <: Tuple: Type](
    sourceValue: Expr[A],
    builder: Expr[Builder[?, A, B, Config]],
    A: DerivingMirror.ProductOf[A],
    B: DerivingMirror.ProductOf[B]
  ): Expr[B] = {
    val config = materializeProductConfig[Config]

    val destinationFields = Field.fromMirror(B).map(field => field.name -> field).toMap
    val sourceFields = Field.fromMirror(A).map(field => field.name -> field).toMap

    val nonConfiguredFields = destinationFields -- config.map(_.name)
    val transformedFields = fieldTransformers(sourceValue, nonConfiguredFields.values.toList, sourceFields)
    val configuredFields =
      fieldConfigurations[A, B](
        config,
        sourceValue,
        '{ $builder.constants },
        '{ $builder.computeds },
        destinationFields
      )

    constructor(TypeRepr.of[B])
      .appliedToArgs(transformedFields ++ configuredFields)
      .asExprOf[B]
  }

  def transform[A: Type, B: Type](
    sourceValue: Expr[A],
    A: DerivingMirror.ProductOf[A],
    B: DerivingMirror.ProductOf[B]
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
    constants: Expr[Map[String, Any]],
    computeds: Expr[Map[String, A => Any]],
    destinationFieldMapping: Map[String, Field]
  ) = config
    .map(cfg => destinationFieldMapping(cfg.name) -> cfg)
    .map { (field, cfg) =>
      val call = cfg match {
        case Product.Const(label)          => '{ $constants(${ Expr(field.name) }) }
        case Product.Computed(label)       => '{ $computeds(${ Expr(field.name) })($sourceValue) }
        case Product.Renamed(dest, source) => accessField(sourceValue, source).asExpr
      }

      val castedCall = field.tpe.asType match {
        case '[fieldTpe] => '{ $call.asInstanceOf[fieldTpe] }
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
    A: DerivingMirror.ProductOf[A],
    B: DerivingMirror.ProductOf[B]
  ): B = ${ transformMacro[A, B]('source, 'A, 'B) }

  inline def transformWithBuilder[A, B, Config <: Tuple](source: A, builder: Builder[?, A, B, Config])(using
    A: DerivingMirror.ProductOf[A],
    B: DerivingMirror.ProductOf[B]
  ): B =
    ${ transformWithBuilderMacro[A, B, Config]('source, 'builder, 'A, 'B) }

  def transformMacro[A: Type, B: Type](
    source: Expr[A],
    A: Expr[DerivingMirror.ProductOf[A]],
    B: Expr[DerivingMirror.ProductOf[B]]
  )(using Quotes): Expr[B] = ProductTransformerMacros().transform(source, A, B)

  def transformWithBuilderMacro[A: Type, B: Type, Config <: Tuple: Type](
    source: Expr[A],
    builder: Expr[Builder[?, A, B, Config]],
    A: Expr[DerivingMirror.ProductOf[A]],
    B: Expr[DerivingMirror.ProductOf[B]]
  )(using Quotes): Expr[B] = ProductTransformerMacros().transformConfigured(source, builder, A, B)

  inline def via[A, B, Func](source: A, inline function: Func)(using
    A: DerivingMirror.ProductOf[A],
    Func: FunctionMirror.Aux[Func, ?, B]
  ): B = ${ viaMacro('source, 'function, 'Func, 'A) }

  def viaMacro[A: Type, B: Type, Func](
    source: Expr[A],
    function: Expr[Func],
    Func: Expr[FunctionMirror.Aux[Func, ?, B]],
    A: Expr[DerivingMirror.ProductOf[A]]
  )(using Quotes) =
    ProductTransformerMacros().via(source, function, Func, A)

  inline def viaWithBuilder[A, B, Func, NamedArgs <: Tuple, Config <: Tuple](
    source: A,
    builder: ViaBuilder[?, A, B, Func, NamedArgs, Config]
  )(using
    A: DerivingMirror.ProductOf[A],
    Func: FunctionMirror.Aux[Func, ?, B]
  ): B = ${ viaWithBuilderMacro('source, 'builder, 'A, 'Func) }

  def viaWithBuilderMacro[A: Type, B: Type, Func: Type, NamedArgs <: Tuple: Type, Config <: Tuple: Type](
    source: Expr[A],
    builder: Expr[ViaBuilder[?, A, B, Func, NamedArgs, Config]],
    A: Expr[DerivingMirror.ProductOf[A]],
    Func: Expr[FunctionMirror.Aux[Func, ?, B]]
  )(using Quotes) =
    ProductTransformerMacros().viaConfigured(source, builder, Func, A)
}
