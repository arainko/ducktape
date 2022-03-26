package io.github.arainko

import scala.quoted.*
import io.github.arainko.Configuration.*

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
  def transformCoproduct[A: Type, B: Type](
    source: Expr[A]
  ) = {
    val destTpe = TypeRepr.of[B]
    val sourceTpe = TypeRepr.of[A]
    val destCases = destTpe.typeSymbol.children.map(sym => sym.name -> sym).toMap
    val sourceCases = sourceTpe.typeSymbol.children.map(sym => sym.name -> sym).toMap

    val associated = destCases.map { (name, dest) => dest -> sourceCases.get(name).getOrElse(report.errorAndAbort("HERE")) }.map { (destCase, sourceCase) =>
      val sourceCaseTpe = TypeIdent(sourceCase)
      val condition = TypeApply(Select.unique(source.asTerm, "isInstanceOf"), sourceCaseTpe :: Nil)
      val action = destTpe.memberType(destCase).asType match {
        case '[destCase] => 
          val valueOf = Expr.summon[ValueOf[destCase]].getOrElse(report.errorAndAbort("BARF"))
          '{ $valueOf.value }.asTerm
      }
      condition -> action
    }

    mkIfStatement(associated.toList).asExprOf[B]
  }

  def mkIfStatement(branches: List[(Term, Term)]): Term = {
    branches match
      case (p1, a1) :: xs =>
        If(p1, a1, mkIfStatement(xs))
      case Nil => ('{ throw RuntimeException("Unhandled condition encountered during derivation") }).asTerm
  }

  def transformWithBuilder[A: Type, B: Type, Config <: Tuple: Type](
    sourceValue: Expr[A],
    builder: Expr[Builder[A, B, Config]]
  ) = {
    val destTpe = TypeRepr.of[B]
    val constructor = destTpe.typeSymbol.primaryConstructor

    val sourceFields = fields[A].map(field => field.name -> field).toMap
    val destinationFields = fields[B].map(field => field.name -> field).toMap
    val config = materializeConfig[Config]

    val nonConfiguredFields = destinationFields -- config.map(_.name)

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

    val configuredFields =
      config
        .map(cfg => destinationFields(cfg.name) -> cfg)
        .map { (field, cfg) =>
          val call = cfg match {
            case Const(label)          => '{ $builder.constants(${ Expr(field.name) }) }
            case Computed(label)       => '{ $builder.computeds(${ Expr(field.name) })($sourceValue) }
            case Renamed(dest, source) => accessField(sourceValue, source).asExpr
          }

          val castedCall = field.tpe.asType match {
            case '[fieldTpe] => '{ $call.asInstanceOf[fieldTpe] }
          }

          NamedArg(field.name, castedCall.asTerm)
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

  def materializeConfig[Config <: Tuple: Type]: List[Configuration] = {
    TypeRepr.of[Config].asType match {
      case '[EmptyTuple] =>
        List.empty
      case '[Const[field] *: tail] =>
        Const(materializeConstantString[field]) :: materializeConfig[tail]
      case '[Computed[field] *: tail] =>
        Computed(materializeConstantString[field]) :: materializeConfig[tail]
      case '[Renamed[dest, source] *: tail] =>
        Renamed(materializeConstantString[dest], materializeConstantString[source]) :: materializeConfig[tail]
    }
  }

  private def materializeConstantString[A <: String: Type] = TypeRepr.of[A] match {
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
    val struct = Printer.TreeStructure.show(value.asTerm)
    '{
      println(${ Expr(struct) })
      $value
    }
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

  // coprod tests
  inline def transformCoproduct[A, B](source: A): B = ${ transformCoproductMacro[A, B]('source) }

  def transformCoproductMacro[A: Type, B: Type](source: Expr[A])(using Quotes): Expr[B] =
    TransformerMacros().transformCoproduct(source)
}
