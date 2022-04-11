package io.github.arainko.ducktape.internal.macros

import scala.quoted.*
import io.github.arainko.ducktape.Configuration.*
import io.github.arainko.ducktape.internal.modules.*
import io.github.arainko.ducktape.*
import scala.deriving.Mirror as DerivingMirror

private[ducktape] class ProductTransformerMacros(using val quotes: Quotes)
    extends Module,
      FieldModule,
      MirrorModule,
      SelectorModule,
      ConfigurationModule {
  import quotes.reflect.*
  import MaterializedConfiguration.*

  def via[A: Type, Func](sourceValue: Expr[A], function: Expr[Func], A: DerivingMirror.ProductOf[A]) = {
    function.asTerm match {
      case func @ FieldSelector.SelectorLambda(vals, body) =>
        val lambdaFields = vals.map(Field.fromValDef)
        val sourceFields = Field.fromMirror(A).map(field => field.name -> field).toMap
        val calls = fieldTransformers(sourceValue, lambdaFields, sourceFields).map(_.value)
        
        Select.unique(func, "apply").appliedToArgs(calls).asExpr
      case other => report.errorAndAbort(other.show(using Printer.TreeStructure))
    }
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
    val configuredFields = fieldConfigurations(config, sourceValue, builder, destinationFields)

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

  private def fieldConfigurations[A: Type, B: Type, Config <: Tuple: Type](
    config: List[MaterializedConfiguration.Product],
    sourceValue: Expr[A],
    builder: Expr[Builder[?, A, B, Config]],
    destinationFieldMapping: Map[String, Field]
  ) = config
    .map(cfg => destinationFieldMapping(cfg.name) -> cfg)
    .map { (field, cfg) =>
      val call = cfg match {
        case Product.Const(label)          => '{ $builder.constants(${ Expr(field.name) }) }
        case Product.Computed(label)       => '{ $builder.computeds(${ Expr(field.name) })($sourceValue) }
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

  transparent inline def via[A, Func](source: A, inline function: Func)(using A: DerivingMirror.ProductOf[A]) =
    ${ viaMacro('source, 'function, 'A) }

  def viaMacro[A: Type, Func](source: Expr[A], func: Expr[Func], A: Expr[DerivingMirror.ProductOf[A]])(using Quotes) =
    ProductTransformerMacros().via[A, Func](source, func, A)
}
