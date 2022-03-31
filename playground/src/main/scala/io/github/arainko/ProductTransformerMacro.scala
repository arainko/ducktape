package io.github.arainko

import scala.quoted.*
import io.github.arainko.Configuration.*
import io.github.arainko.internal.*
import scala.deriving.Mirror as DerivingMirror

/*
  General idea:
    1. Resolve companion of destination type
    2. Resolve set of fields of destination type
    3. Resolve set of fields of source type
    4. Resolve transformers for intersection of fields
    5. Optimize indetity transformers to field accessors
    6. Apply transformers
 */

class ProductTransformerMacros(using val quotes: Quotes)
    extends Module,
      FieldModule,
      MirrorModule,
      SelectorModule,
      ConfigurationModule {
  import quotes.reflect.*
  import MaterializedConfiguration.*

  /*
    Idea:
      1. Associate by name
      2. Try to resolve singletons for destination and source types
      3. Construct one big if statement that the associated by name symbol types and matches it to the other one

      enum Enum1:
        case Case1
        case Case2
        case Case3

      enum Enum2:
        case Case1
        case Case2
        case Case3

      Enum1 to Enum2:
        def transform(value: Enum1): Enum2 =
          if (value.isInstanceOf[Enum1.Case1]) Enum2.Case1
          else if (value.isInstanceOf[Enum1.Case2]) Enum2.Case2
          else if (value.isInstanceOf[Enum1.Case3]) Enum2.Case3
          else throw new Exception("Unknown case")

   */

  def transformConfigured[A: Type, B: Type, Config <: Tuple: Type](
    sourceValue: Expr[A],
    builder: Expr[Builder[A, B, Config]],
    A: Expr[DerivingMirror.ProductOf[A]],
    B: Expr[DerivingMirror.ProductOf[B]]
  ): Expr[B] = {
    val destTpe = TypeRepr.of[B]
    val constructor = destTpe.typeSymbol.primaryConstructor
    val config = materializeProductConfig[Config]

    val destinationFields = Field.fromMirror(B).map(field => field.name -> field).toMap
    val sourceFields = Field.fromMirror(A).map(field => field.name -> field).toMap

    val nonConfiguredFields = destinationFields -- config.map(_.name)
    val transformedFields = fieldTransformers(sourceValue, nonConfiguredFields.values.toList, sourceFields)
    val configuredFields = fieldConfigurations(config, sourceValue, builder, destinationFields)

    New(Inferred(destTpe))
      .select(constructor)
      .appliedToArgs(transformedFields ++ configuredFields)
      .asExprOf[B]
  }

  def transform[A: Type, B: Type](
    sourceValue: Expr[A],
    A: Expr[DerivingMirror.ProductOf[A]],
    B: Expr[DerivingMirror.ProductOf[B]]
  ): Expr[B] = {
    val destTpe = TypeRepr.of[B]
    val constructor = destTpe.typeSymbol.primaryConstructor

    val destinationFields = Field.fromMirror(B)
    val sourceFields = Field.fromMirror(A).map(field => field.name -> field).toMap
    val accessedFields = fieldTransformers(sourceValue, destinationFields, sourceFields)

    New(Inferred(destTpe))
      .select(constructor)
      .appliedToArgs(accessedFields.toList)
      .asExprOf[B]
  }

  private def fieldTransformers[A: Type](
    sourceValue: Expr[A],
    destinationFields: List[Field],
    sourceFieldMapping: Map[String, Field]
  ) =
    destinationFields
      .map(field => field -> sourceFieldMapping.get(field.name).getOrElse(report.errorAndAbort(s"Not found for ${field.name}")))
      .map { (dest, source) =>
        val call = resolveTransformer(sourceValue, source, dest)
        NamedArg(dest.name, call)
      }

  private def fieldConfigurations[A: Type, B: Type, Config <: Tuple: Type](
    config: List[MaterializedConfiguration.Product],
    sourceValue: Expr[A],
    builder: Expr[Builder[A, B, Config]],
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
    (source.tpe.asType, destination.tpe.asType) match {
      case ('[source], '[dest]) =>
        Expr
          .summon[Transformer[source, dest]]
          .map {
            case '{ $transformer: Transformer.Identity[source] } => accessField(sourceValue, source.name)
            case '{ $transformer: Transformer[source, dest] } =>
              val field = accessField(sourceValue, source.name).asExprOf[source]
              '{ $transformer.transform($field) }.asTerm
          }
          .getOrElse(report.errorAndAbort(s"Transformer not found ###"))
    }

  private def accessField[A: Type](value: Expr[A], fieldName: String) = Select.unique(value.asTerm, fieldName)

}

object Macros {
  inline def structure[A](inline value: A) = ${ structureMacro('value) }
  def structureMacro[A: Type](value: Expr[A])(using Quotes) = {
    import quotes.reflect.*
    val struct = Printer.TreeStructure.show(value.asTerm)
    '{
      println(${ Expr(struct) })
      $value
    }
  }

  inline def code[A](inline value: A) = ${ codeMacro('value) }
  def codeMacro[A: Type](value: Expr[A])(using Quotes) = {
    import quotes.reflect.*
    val struct = Printer.TreeAnsiCode.show(value.asTerm)
    '{
      println(${ Expr(struct) })
      $value
    }
  }

  inline def transform[A, B](source: A)(using
    A: DerivingMirror.ProductOf[A],
    B: DerivingMirror.ProductOf[B]
  ): B = ${ transformMacro[A, B]('source, 'A, 'B) }

  def transformMacro[A: Type, B: Type](
    source: Expr[A],
    A: Expr[DerivingMirror.ProductOf[A]],
    B: Expr[DerivingMirror.ProductOf[B]]
  )(using Quotes): Expr[B] = ProductTransformerMacros().transform(source, A, B)

  

  // inline def transformWithBuilder[A, B, Config <: Tuple](source: A, builder: Builder[A, B, Config]): B =
  //   ${ transformWithBuilderMacro('source, 'builder) }

  // def transformWithBuilderMacro[A: Type, B: Type, Config <: Tuple: Type](
  //   source: Expr[A],
  //   builder: Expr[Builder[A, B, Config]]
  // )(using Quotes): Expr[B] =
  //   TransformerMacros().transformWithBuilder(source, builder)

  // coprod tests
  // inline def transformCoproduct[A] = ${ transformCoproductMacro[A] }

  // def transformCoproductMacro[A: Type](using Quotes) =
  // TransformerMacros().transformCoproduct
}
