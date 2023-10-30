package io.github.arainko.ducktape.internal.macros

import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.function.*
import io.github.arainko.ducktape.internal.modules.MaterializedConfiguration.*
import io.github.arainko.ducktape.internal.modules.*

import scala.deriving.*
import scala.quoted.*

//TODO: if this is moved to `modules` the compiler crashes, investigate further (?)
private[ducktape] object ProductTransformations {

  def transform[Source: Type, Dest: Type](
    sourceValue: Expr[Source],
    Source: Expr[Mirror.ProductOf[Source]],
    Dest: Expr[Mirror.ProductOf[Dest]]
  )(using Quotes): Expr[Dest] = {
    import quotes.reflect.*

    given Fields.Source = Fields.Source.fromMirror(Source)
    given Fields.Dest = Fields.Dest.fromMirror(Dest)

    val transformerFields = fieldTransformations(sourceValue, Fields.dest.value)

    Constructor(TypeRepr.of[Dest])
      .appliedToArgs(transformerFields.toList)
      .asExprOf[Dest]
  }

  def transformConfigured[Source: Type, Dest: Type](
    sourceValue: Expr[Source],
    config: Expr[Seq[BuilderConfig[Source, Dest]]],
    Source: Expr[Mirror.ProductOf[Source]],
    Dest: Expr[Mirror.ProductOf[Dest]]
  )(using Quotes): Expr[Dest] = {
    import quotes.reflect.*

    given Fields.Source = Fields.Source.fromMirror(Source)
    given Fields.Dest = Fields.Dest.fromMirror(Dest)

    // val materializedConfig = MaterializedConfiguration.Product.fromFieldConfig(config)
    val nonConfiguredFields = Fields.dest.byName
    val transformedFields = fieldTransformations(sourceValue, nonConfiguredFields.values.toList)
    // val configuredFields = fieldConfigurations(materializedConfig, sourceValue)

    Constructor(TypeRepr.of[Dest])
      .appliedToArgs(transformedFields)
      .asExprOf[Dest]
  }

  def via[Source: Type, Dest: Type, Func](
    sourceValue: Expr[Source],
    function: Expr[Func],
    Func: Expr[FunctionMirror.Aux[Func, Dest]],
    Source: Expr[Mirror.ProductOf[Source]]
  )(using Quotes): Expr[Dest] = {
    import quotes.reflect.*

    function.asTerm match {
      case func @ FunctionLambda(vals, _) =>
        given Fields.Source = Fields.Source.fromMirror(Source)
        given Fields.Dest = Fields.Dest.fromValDefs(vals)

        val calls = fieldTransformations(sourceValue, Fields.dest.value).map(_.value)
        Select.unique(func, "apply").appliedToArgs(calls).asExprOf[Dest]
      case other => report.errorAndAbort(s"'via' is only supported on eta-expanded methods!")
    }
  }

  def viaConfigured[Source: Type, Dest: Type, Func: Type, ArgSelector <: FunctionArguments: Type](
    sourceValue: Expr[Source],
    function: Expr[Func],
    config: Expr[Seq[ArgBuilderConfig[Source, Dest, ArgSelector]]],
    Source: Expr[Mirror.ProductOf[Source]]
  )(using Quotes): Expr[Dest] = {
    import quotes.reflect.*

    given Fields.Source = Fields.Source.fromMirror(Source)
    given Fields.Dest = Fields.Dest.fromFunctionArguments[ArgSelector]
    val materializedConfig = MaterializedConfiguration.Product.fromArgConfig(config)
    val nonConfiguredFields = Fields.dest.byName -- materializedConfig.map(_.destFieldName)

    val transformedFields =
      fieldTransformations(sourceValue, nonConfiguredFields.values.toList)
        .map(field => field.name -> field)
        .toMap

    val configuredFields =
      fieldConfigurations(
        materializedConfig,
        sourceValue
      ).map(field => field.name -> field).toMap

    val callsInOrder = Fields.dest.value.map { field =>
      transformedFields
        .get(field.name)
        .getOrElse(configuredFields(field.name))
        .value
    }

    Select
      .unique(function.asTerm, "apply")
      .appliedToArgs(callsInOrder)
      .asExprOf[Dest]
  }

  def transformFromAnyVal[Source: Type, Dest: Type](
    sourceValue: Expr[Source]
  )(using Quotes): Expr[Dest] = {
    import quotes.reflect.*

    val tpe = TypeRepr.of[Source]
    val fieldSymbol =
      tpe.typeSymbol.fieldMembers.headOption
        .getOrElse(report.errorAndAbort(s"Failed to fetch the wrapped field name of ${tpe.show}"))

    sourceValue.accessFieldByName(fieldSymbol.name).asExprOf[Dest]
  }

  def transformToAnyVal[Source: Type, Dest: Type](
    sourceValue: Expr[Source]
  )(using Quotes): Expr[Dest] = {
    import quotes.reflect.*

    Constructor(TypeRepr.of[Dest])
      .appliedTo(sourceValue.asTerm)
      .asExprOf[Dest]
  }

  private def fieldTransformations[Source: Type](
    sourceValue: Expr[Source],
    fieldsToTransformInto: List[Field]
  )(using Quotes, Fields.Source) = {
    import quotes.reflect.*

    fieldsToTransformInto.map { field =>
      field ->
        Fields.source
          .get(field.name)
          .getOrElse(Failure.emit(Failure.NoFieldMapping(field.name, Type.of[Source])))
    }.map { (dest, source) =>
      val call = resolveTransformation(sourceValue, source, dest)

      NamedArg(dest.name, call)
    }
  }

  private def fieldConfigurations[Source: Type](
    config: List[MaterializedConfiguration.Product],
    sourceValue: Expr[Source]
  )(using Quotes, Fields.Dest) = {
    import quotes.reflect.*

    config
      .map(cfg => Fields.dest.unsafeGet(cfg.destFieldName) -> cfg)
      .map { (field, cfg) =>
        val call = cfg match {
          case Product.Const(label, value)       => value
          case Product.Computed(label, function) => '{ $function($sourceValue) }
          case Product.Renamed(dest, source)     => sourceValue.accessFieldByName(source).asExpr
        }

        val castedCall = field.tpe match {
          case '[fieldTpe] => call.asExprOf[fieldTpe]
        }

        NamedArg(field.name, castedCall.asTerm)
      }
  }

  private def resolveTransformation[Source: Type](
    sourceValue: Expr[Source],
    source: Field,
    destination: Field
  )(using Quotes) = {
    import quotes.reflect.*

    source.transformerTo(destination) match {
      // even though this is taken care of in LiftTransformation.liftTransformation
      // we need to do this here due to a compiler bug where multiple matches on a
      // Transformer[A, B >: A] the B type get replaced with `B | dest` but only if
      // you refer to a case class defined in an object by NOT its full path (it works if you refer to it as the full path)
      // workaround for issue: https://github.com/arainko/ducktape/issues/26 until this gets fixed in dotty.
      case '{
            type a
            $transformer: Transformer.Identity[`a`, `a`]
          } =>
        sourceValue.accessField(source)
      case '{ $transformer: Transformer[source, dest] } =>
        val field = sourceValue.accessField(source).asExprOf[source]
        // '{ $transformer.transform($field) }.asTerm
        LiftTransformation.liftTransformation(transformer, field).asTerm
    }
  }
}
