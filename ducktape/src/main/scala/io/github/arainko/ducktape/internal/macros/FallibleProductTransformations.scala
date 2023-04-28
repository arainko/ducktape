package io.github.arainko.ducktape.internal.macros

import io.github.arainko.ducktape.function.FunctionArguments
import io.github.arainko.ducktape.internal.modules.*
import io.github.arainko.ducktape.internal.util.*
import io.github.arainko.ducktape.{ Field as _, * }

import scala.deriving.Mirror
import scala.quoted.*

import Transformer.*

abstract class FallibleProductTransformations[
  Support[f[+x]] <: Mode[f]
] {
  final def transform[F[+x]: Type, Source: Type, Dest: Type](
    Source: Expr[Mirror.ProductOf[Source]],
    Dest: Expr[Mirror.ProductOf[Dest]],
    F: Expr[Support[F]],
    sourceValue: Expr[Source]
  )(using Quotes): Expr[F[Dest]] = {
    import quotes.reflect.*

    given Fields.Source = Fields.Source.fromMirror(Source)
    given Fields.Dest = Fields.Dest.fromMirror(Dest)

    createTransformation[F, Source, Dest](F, sourceValue, Fields.dest.value, Nil, Nil)(Constructor.construct[Dest])
  }

  final def transformConfigured[F[+x]: Type, Source: Type, Dest: Type](
    Source: Expr[Mirror.ProductOf[Source]],
    Dest: Expr[Mirror.ProductOf[Dest]],
    F: Expr[Support[F]],
    config: Expr[Seq[FallibleBuilderConfig[F, Source, Dest] | BuilderConfig[Source, Dest]]],
    sourceValue: Expr[Source]
  )(using Quotes): Expr[F[Dest]] = {
    import quotes.reflect.*

    given Fields.Source = Fields.Source.fromMirror(Source)
    given Fields.Dest = Fields.Dest.fromMirror(Dest)

    val materializedConfig = MaterializedConfiguration.FallibleProduct.fromFallibleFieldConfig(config)
    val nonConfiguredFields = (Fields.dest.byName -- materializedConfig.map(_.destFieldName)).values.toList
    val (wrappedFields, unwrappedFields) = configuredFieldTransformations(materializedConfig, sourceValue)

    createTransformation(F, sourceValue, nonConfiguredFields, unwrappedFields, wrappedFields)(Constructor.construct[Dest])
  }

  final def via[F[+x]: Type, Source: Type, Dest: Type, Func](
    sourceValue: Expr[Source],
    function: Expr[Func],
    Source: Expr[Mirror.ProductOf[Source]],
    F: Expr[Support[F]]
  )(using Quotes): Expr[F[Dest]] = {
    import quotes.reflect.*

    function.asTerm match {
      case func @ FunctionLambda(vals, _) =>
        given Fields.Source = Fields.Source.fromMirror(Source)
        given Fields.Dest = Fields.Dest.fromValDefs(vals)

        createTransformation[F, Source, Dest](F, sourceValue, Fields.dest.value, Nil, Nil) { unwrappedFields =>
          val rearrangedFields = rearrangeFieldsToDestOrder(unwrappedFields).map(_.value.asTerm)
          Select.unique(func, "apply").appliedToArgs(rearrangedFields).asExprOf[Dest]
        }
      case other => report.errorAndAbort(s"'via' is only supported on eta-expanded methods!")
    }
  }

  final def viaConfigured[F[+x]: Type, Source: Type, Dest: Type, Func: Type, ArgSelector <: FunctionArguments: Type](
    sourceValue: Expr[Source],
    function: Expr[Func],
    config: Expr[Seq[FallibleArgBuilderConfig[F, Source, Dest, ArgSelector] | ArgBuilderConfig[Source, Dest, ArgSelector]]],
    Source: Expr[Mirror.ProductOf[Source]],
    F: Expr[Support[F]]
  )(using Quotes): Expr[F[Dest]] = {
    import quotes.reflect.*

    given Fields.Source = Fields.Source.fromMirror(Source)
    given Fields.Dest = Fields.Dest.fromFunctionArguments[ArgSelector]

    val materializedConfig = MaterializedConfiguration.FallibleProduct.fromFallibleArgConfig(config)
    val nonConfiguredFields = (Fields.dest.byName -- materializedConfig.map(_.destFieldName)).values.toList
    val (wrappedFields, unwrappedFields) = configuredFieldTransformations(materializedConfig, sourceValue)

    createTransformation[F, Source, Dest](F, sourceValue, nonConfiguredFields, unwrappedFields, wrappedFields) {
      unwrappedFields =>
        val rearrangedFields = rearrangeFieldsToDestOrder(unwrappedFields).map(_.value.asTerm)
        Select.unique(function.asTerm, "apply").appliedToArgs(rearrangedFields).asExprOf[Dest]
    }
  }

  protected def createTransformation[F[+x]: Type, Source: Type, Dest: Type](
    F: Expr[Support[F]],
    sourceValue: Expr[Source],
    fieldsToTransformInto: List[Field],
    unwrappedFieldsFromConfig: List[Field.Unwrapped],
    wrappedFieldsFromConfig: List[Field.Wrapped[F]]
  )(construct: List[Field.Unwrapped] => Expr[Dest])(using Quotes, Fields.Source): Expr[F[Dest]]

  private def configuredFieldTransformations[F[+x]: Type, Source: Type](
    configs: List[MaterializedConfiguration.FallibleProduct[F]],
    sourceValue: Expr[Source]
  )(using Quotes, Fields.Dest): (List[Field.Wrapped[F]], List[Field.Unwrapped]) = {
    import quotes.reflect.*
    import MaterializedConfiguration.*

    val wrappedFields = List.newBuilder[Field.Wrapped[F]]
    val unwrappedFields = List.newBuilder[Field.Unwrapped]

    configs.foreach { cfg =>
      (Fields.dest.unsafeGet(cfg.destFieldName) -> cfg) match {
        case (field, FallibleProduct.Const(_, value)) =>
          wrappedFields += Field.Wrapped(field, value)
        case (field, FallibleProduct.Computed(_, function)) =>
          wrappedFields += Field.Wrapped(field, '{ $function($sourceValue) })
        case (field, FallibleProduct.Total(Product.Const(_, value))) =>
          unwrappedFields += Field.Unwrapped(field, value)
        case (field, FallibleProduct.Total(Product.Computed(_, function))) =>
          unwrappedFields += Field.Unwrapped(field, '{ $function($sourceValue) })
        case (field, FallibleProduct.Total(Product.Renamed(destField, sourceField))) =>
          unwrappedFields += Field.Unwrapped(field, sourceValue.accessFieldByName(sourceField).asExpr)
      }
    }

    wrappedFields.result() -> unwrappedFields.result()
  }

  private def rearrangeFieldsToDestOrder(fields: List[Field.Unwrapped])(using Fields.Dest) = {
    val unwrappedByName = fields.map(field => field.underlying.name -> field).toMap
    Fields.dest.value.map(field => unwrappedByName(field.name))
  }
}
