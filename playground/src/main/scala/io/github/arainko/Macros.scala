package io.github.arainko

import scala.quoted.*
import java.util.Objects

/*
  General idea:
    1. Resolve companion of destination type
    2. Resolve set of fields of destination type
    3. Resolve set of fields of source type
    4. Resolve transformers for intersection of fields
    5. Optimize indetity transformers to field accessors
    6. Apply transformers
 */

class TransformerMacros(using val quotes: Quotes) {
  import quotes.reflect.*

  def transformWithBuilder[A: Type, B: Type, Config <: Tuple: Type](
    sourceValue: Expr[A],
    builder: Expr[Builder[A, B, Config]]
  ) = {
    val destTpe = TypeRepr.of[B]
    val constructor = destTpe.typeSymbol.primaryConstructor

    val sourceFields = fields[A].map(field => field.name -> field).toMap
    val destinationFields = fields[B].map(field => field.name -> field).toMap
    val config = materializeConfig[Config]

    val nonConfiguredFields = destinationFields -- config

    val accessedNonConfiguredFields = nonConfiguredFields
      .map((name, field) => field -> sourceFields.get(name).getOrElse(report.errorAndAbort(s"Not found for $name")))
      .map { (dest, source) =>
        val call = resolveTransformer(source.tpe, dest.tpe) match {
          case '{ $transformer: Transformer.Identity[source] } => accessField(sourceValue, source.name)
          case '{ $transformer: Transformer[source, dest] } =>
            val field = accessField(sourceValue, source.name).asExprOf[source]
            '{ $transformer.transform($field) }.asTerm
        }

        NamedArg(dest.name, call)
      }

    val configuredFields = config.map(destinationFields).map { field =>
      val call = field.tpe.asType match {
        case '[fieldTpe] => '{ $builder.constants(${ Expr(field.name) }).asInstanceOf[fieldTpe] }.asTerm
      }
      NamedArg(field.name, call)
    }

    New(Inferred(destTpe))
      .select(constructor)
      .appliedToArgs(accessedNonConfiguredFields.toList ++ configuredFields)
      .asExprOf[B]
  }

  def transform[A: Type, B: Type](sourceValue: Expr[A]): Expr[B] = {
    val destTpe = TypeRepr.of[B]
    val constructor = destTpe.typeSymbol.primaryConstructor

    val sourceFields = fields[A].map(field => field.name -> field).toMap
    val destinationFields = fields[B].map(field => field.name -> field).toMap

    val accessedFields = destinationFields
      .map((name, field) => field -> sourceFields.get(name).getOrElse(report.errorAndAbort(s"Not found for $name")))
      .map { (dest, source) =>
        val call = resolveTransformer(source.tpe, dest.tpe) match {
          case '{ $transformer: Transformer.Identity[source] } => accessField(sourceValue, source.name)
          case '{ $transformer: Transformer[source, dest] } =>
            val field = accessField(sourceValue, source.name).asExprOf[source]
            '{ $transformer.transform($field) }.asTerm
        }

        NamedArg(dest.name, call)
      }

    New(Inferred(destTpe))
      .select(constructor)
      .appliedToArgs(accessedFields.toList)
      .asExprOf[B]
  }

  private def fields[A: Type]: List[Field] = {
    val tpe = TypeRepr.of[A]
    tpe.classSymbol.get.caseFields.zipWithIndex.map((symbol, idx) => Field(tpe, symbol, idx))
  }

  private def accessField[A: Type](value: Expr[A], fieldName: String) = Select.unique(value.asTerm, fieldName)

  private def resolveTransformer(source: TypeRepr, destination: TypeRepr) =
    (source.asType, destination.asType) match {
      case ('[source], '[dest]) =>
        Expr
          .summon[Transformer[source, dest]]
          .getOrElse(report.errorAndAbort(s"Transformer not found ###"))
    }

  def materializeConfig[Config <: Tuple: Type]: List[String] = {
    TypeRepr.of[Config].asType match {
      case '[EmptyTuple]           => List.empty
      case '[Const[field] *: tail] => materializeConstantString[field] :: materializeConfig[tail]
    }
  }

  private def materializeConstantString[A: Type] = TypeRepr.of[A] match {
    case ConstantType(StringConstant(value)) => value
    case other                               => report.errorAndAbort("Type is not a String!")
  }

  private final class Field(parentTpe: TypeRepr, symbol: Symbol, index: Int) {
    val name: String = symbol.name
    val tpe: TypeRepr = parentTpe.memberType(symbol)
  }
}

object Macros {
  inline def structure[A](inline value: A) = ${ structureMacro('value) }
  def structureMacro[A: Type](value: Expr[A])(using Quotes) = {
    import quotes.reflect.*
    val struct = Printer.TreeShortCode.show(value.asTerm)
    '{ println(${ Expr(struct) }) }
  }

  inline def transform[A, B](source: A): B = ${ transformMacro[A, B]('source) }
  def transformMacro[A: Type, B: Type](source: Expr[A])(using Quotes): Expr[B] = TransformerMacros().transform(source)

  inline def transformWithBuilder[A, B, Config <: Tuple](source: A, builder: Builder[A, B, Config]): B =
    ${ transformWithBuilderMacro('source, 'builder) }

  def transformWithBuilderMacro[A: Type, B: Type, Config <: Tuple: Type](
    source: Expr[A],
    builder: Expr[Builder[A, B, Config]]
  )(using Quotes): Expr[B] =
    TransformerMacros().transformWithBuilder(source, builder)
}
