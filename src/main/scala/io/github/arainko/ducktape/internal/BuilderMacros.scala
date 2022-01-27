package io.github.arainko.ducktape.internal

import scala.quoted.*
import scala.compiletime.*
import io.github.arainko.ducktape.Field
import scala.deriving.Mirror
import io.github.arainko.ducktape.builder.Builder

object BuilderMacros {

  transparent inline def dropCompiletimeField[
    SpecificBuilder[_, _, _ <: Tuple, _ <: Tuple, _ <: Tuple, _ <: Tuple],
    From,
    To,
    FromSubcases <: Tuple,
    ToSubcases <: Tuple,
    DerivedFromSubcases <: Tuple,
    DerivedToSubcases <: Tuple,
    FieldType
  ](
    inline builder: SpecificBuilder[From, To, FromSubcases, ToSubcases, DerivedFromSubcases, DerivedToSubcases],
    inline selector: To => FieldType
  ) = ${ dropCompiletimeFieldMacro('builder, 'selector) }

  transparent inline def dropCompiletimeFieldsForRename[
    SpecificBuilder[_, _, _ <: Tuple, _ <: Tuple, _ <: Tuple, _ <: Tuple],
    From,
    To,
    FromSubcases <: Tuple,
    ToSubcases <: Tuple,
    DerivedFromSubcases <: Tuple,
    DerivedToSubcases <: Tuple,
    ToFieldType,
    FromFieldType
  ](
    inline builder: SpecificBuilder[From, To, FromSubcases, ToSubcases, DerivedFromSubcases, DerivedToSubcases],
    inline toSelector: To => ToFieldType,
    inline fromSelector: From => FromFieldType
  ) = ${ dropCompiletimeFieldsForRenameMacro('builder, 'toSelector, 'fromSelector) }

  inline def selectedField[From, FieldType](inline selector: From => FieldType): String =
    ${ selectedFieldMacro[From, FieldType]('selector) }

  def selectedFieldMacro[From: Type, FieldType: Type](selector: Expr[From => FieldType])(using Quotes) =
    Expr(BuilderMacros().selectedField(selector))
    
  def dropCompiletimeFieldMacro[
    SpecificBuilder[_, _, _ <: Tuple, _ <: Tuple, _ <: Tuple, _ <: Tuple]: Type,
    From: Type,
    To: Type,
    FromSubcases <: Tuple: Type,
    ToSubcases <: Tuple: Type,
    DerivedFromSubcases <: Tuple: Type,
    DerivedToSubcases <: Tuple: Type,
    FieldType: Type
  ](
    builder: Expr[SpecificBuilder[From, To, FromSubcases, ToSubcases, DerivedFromSubcases, DerivedToSubcases]],
    selector: Expr[To => FieldType]
  )(using Quotes) = BuilderMacros().dropCompiletimeField(builder, selector)

  def dropCompiletimeFieldsForRenameMacro[
    SpecificBuilder[_, _, _ <: Tuple, _ <: Tuple, _ <: Tuple, _ <: Tuple]: Type,
    From: Type,
    To: Type,
    FromSubcases <: Tuple: Type,
    ToSubcases <: Tuple: Type,
    DerivedFromSubcases <: Tuple: Type,
    DerivedToSubcases <: Tuple: Type,
    ToFieldType: Type,
    FromFieldType: Type
  ](
    builder: Expr[SpecificBuilder[From, To, FromSubcases, ToSubcases, DerivedFromSubcases, DerivedToSubcases]],
    toSelector: Expr[To => ToFieldType],
    fromSelector: Expr[From => FromFieldType]
  )(using Quotes) = BuilderMacros().dropCompiletimeFieldsForRename(builder, toSelector, fromSelector)

}

class BuilderMacros(using val quotes: Quotes) {
  import quotes.reflect.*

  def selectedField[From: Type, FieldType](lambda: Expr[From => FieldType]): String = {
    val validFields = TypeRepr.of[From].typeSymbol.caseFields.map(_.name)
    lambda.asTerm match {
      case FieldSelector(fieldName) if validFields.contains(fieldName) => fieldName
      case _                                                           => report.errorAndAbort("Not a field selector!")
    }
  }

  def dropCompiletimeField[
    SpecificBuilder[_, _, _ <: Tuple, _ <: Tuple, _ <: Tuple, _ <: Tuple]: Type,
    From: Type,
    To: Type,
    FromSubcases <: Tuple: Type,
    ToSubcases <: Tuple: Type,
    DerivedFromSubcases <: Tuple: Type,
    DerivedToSubcases <: Tuple: Type,
    FieldType: Type
  ](
    builder: Expr[SpecificBuilder[From, To, FromSubcases, ToSubcases, DerivedFromSubcases, DerivedToSubcases]],
    selector: Expr[To => FieldType]
  ) = {
    val selectedFieldName = selectedField(selector)
    constantString(selectedFieldName) match {
      case '[field] =>
        '{
          $builder.asInstanceOf[
            SpecificBuilder[
              From,
              To,
              FromSubcases,
              ToSubcases,
              Field.DropByLabel[field, DerivedFromSubcases],
              Field.DropByLabel[field, DerivedToSubcases]
            ]
          ]
        }
    }
  }

  def dropCompiletimeFieldsForRename[
    SpecificBuilder[_, _, _ <: Tuple, _ <: Tuple, _ <: Tuple, _ <: Tuple]: Type,
    From: Type,
    To: Type,
    FromSubcases <: Tuple: Type,
    ToSubcases <: Tuple: Type,
    DerivedFromSubcases <: Tuple: Type,
    DerivedToSubcases <: Tuple: Type,
    ToFieldType: Type,
    FromFieldType: Type
  ](
    builder: Expr[SpecificBuilder[From, To, FromSubcases, ToSubcases, DerivedFromSubcases, DerivedToSubcases]],
    toSelector: Expr[To => ToFieldType],
    fromSelector: Expr[From => FromFieldType]
  ) = {
    val selectedToFieldName = selectedField(toSelector)
    val selectedFromFieldName = selectedField(fromSelector)
    (constantString(selectedToFieldName) -> constantString(selectedFromFieldName)) match {
      case ('[toField], '[fromField]) =>
        '{
          $builder.asInstanceOf[
            SpecificBuilder[
              From,
              To,
              FromSubcases,
              ToSubcases,
              Field.DropByLabel[fromField, DerivedFromSubcases],
              Field.DropByLabel[toField, DerivedToSubcases]
            ]
          ]
        }
    }
  }

  private def constantString(value: String) =
    ConstantType(StringConstant(value)).asType

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
