package io.github.arainko.ducktape.internal

import scala.quoted.*
import io.github.arainko.ducktape.Field
import scala.deriving.Mirror
import io.github.arainko.ducktape.builder.Builder
// import io.github.arainko.ducktape.internal.BuilderMacros.FieldSelect

object BuilderMacros {
  // transparent inline def droppedWithLambda[
  //   From,
  //   FieldType,
  //   Fields <: Tuple
  // ](inline lambda: From => FieldType) =
  //   ${ droppedWithLambdaImpl[From, FieldType, Fields]('lambda, 5) }

  transparent inline def withCompiletimeFieldDropped[
    SpecificBuilder[_, _, _ <: Tuple, _ <: Tuple, _ <: Tuple, _ <: Tuple],
    From,
    To,
    FromSubcases <: Tuple,
    ToSubcases <: Tuple,
    DerivedFromSubcases <: Tuple,
    DerivedToSubcases <: Tuple,
    FieldType
  ](
    inline builder: Builder[SpecificBuilder, From, To, FromSubcases, ToSubcases, DerivedFromSubcases, DerivedToSubcases],
    inline selector: From => FieldType
  ): Builder[SpecificBuilder, From, To, FromSubcases, ToSubcases, ?, ?] = ${ withCompiletimeFieldDroppedMacro('builder, 'selector) }

  def withCompiletimeFieldDroppedMacro[
    SpecificBuilder[_, _, _ <: Tuple, _ <: Tuple, _ <: Tuple, _ <: Tuple]: Type,
    From: Type,
    To: Type,
    FromSubcases <: Tuple: Type,
    ToSubcases <: Tuple: Type,
    DerivedFromSubcases <: Tuple: Type,
    DerivedToSubcases <: Tuple: Type,
    FieldType: Type
  ](
    builder: Expr[Builder[SpecificBuilder, From, To, FromSubcases, ToSubcases, DerivedFromSubcases, DerivedToSubcases]],
    selector: Expr[From => FieldType]
  )(using Quotes) = BuilderMacros().withFieldConstPhantom(builder, selector)

  // def droppedWithLambdaImpl[
  //   From: Type,
  //   FieldType: Type,
  //   Fields <: Tuple: Type
  // ](lambda: Expr[From => FieldType], costam: Int)(using Quotes) =
  //   BuilderMacros().selectFromInlinedLambda[From, FieldType, Fields](lambda, costam)

}

class BuilderMacros(using val quotes: Quotes) {
  import quotes.reflect.*

  private def selectedField[From: Type, FieldType](lambda: Expr[From => FieldType]): String = {
    val validFields = TypeRepr.of[From].typeSymbol.caseFields.map(_.name)
    lambda.asTerm match {
      case FieldSelector(fieldName) if validFields.contains(fieldName) => fieldName
      case _                                                           => report.errorAndAbort("Not a field selector!")
    }
  }

  def withFieldConstPhantom[
    SpecificBuilder[_, _, _ <: Tuple, _ <: Tuple, _ <: Tuple, _ <: Tuple]: Type,
    From: Type,
    To: Type,
    FromSubcases <: Tuple: Type,
    ToSubcases <: Tuple: Type,
    DerivedFromSubcases <: Tuple: Type,
    DerivedToSubcases <: Tuple: Type,
    FieldType: Type
  ](
    builder: Expr[Builder[SpecificBuilder, From, To, FromSubcases, ToSubcases, DerivedFromSubcases, DerivedToSubcases]],
    selector: Expr[From => FieldType]
  ) = {
    val selectedFieldName = selectedField[From, FieldType](selector)
    val fieldNameType = ConstantType(StringConstant(selectedFieldName)).asType
    fieldNameType match {
      case '[field] =>
        builder match {
          case '{ $expr } => ???
        }

        '{
          val modifiedBuilder: Builder[
              SpecificBuilder,
              From,
              To,
              FromSubcases,
              ToSubcases,
              Field.DropByLabel[field, DerivedFromSubcases],
              Field.DropByLabel[field, DerivedToSubcases]
            ] = $builder.asInstanceOf
          modifiedBuilder
        }
    }
  }

  // def selectFromInlinedLambda[
  //   From: Type,
  //   FieldType,
  //   Fields <: Tuple: Type
  // ](lambda: Expr[From => FieldType], costam: Int) = {
  //   given Printer[Tree] = Printer.TreeStructure
  //   println(lambda.asTerm.show)
  //   val selectedFieldName = selectedField[From, FieldType](lambda)
  //   val fieldNameType = ConstantType(StringConstant(selectedFieldName)).asType
  //   val droppedType = fieldNameType match {
  //     case '[field] => Type.of[Field.DropByLabel[field, Fields]]
  //   }
  //   droppedType match {
  //     case '[dropped] =>
  //       '{
  //         val fieldName = ${ Expr(selectedFieldName) }
  //         println(${ Expr(costam) })
  //         ??? : dropped
  //       }
  //   }
  // }

  object FieldSelector:
    private object SelectorLambda:
      def unapply(arg: Term): Option[(List[ValDef], Term)] =
        arg match {
          case Inlined(_, _, Lambda(vals, term)) => Some((vals, term))
          case Inlined(_, _, nested)             => SelectorLambda.unapply(nested)
          case t                                 => None
        }
    end SelectorLambda

    def unapply(arg: Term): Option[String] =
      arg match {
        case SelectorLambda(_, Select(Ident(_), fieldName)) => Some(fieldName)
        case _                                              => None
      }
  end FieldSelector

}
