package io.github.arainko

import scala.quoted.*

class SelectorMacros(using val quotes: Quotes) {
  import quotes.reflect.*

  def selectedField[From: Type, FieldType](lambda: Expr[From => FieldType]): String = {
    val validFields = TypeRepr.of[From].typeSymbol.caseFields.map(_.name)
    lambda.asTerm match {
      case FieldSelector(fieldName) if validFields.contains(fieldName) => fieldName
      case _                                                           => report.errorAndAbort("Not a field selector!")
    }
  }

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

object SelectorMacros {
  inline def selectedField[From, FieldType](inline selector: From => FieldType): String =
    ${ selectedFieldMacro[From, FieldType]('selector) }

  def selectedFieldMacro[From: Type, FieldType: Type](selector: Expr[From => FieldType])(using Quotes) =
    Expr(SelectorMacros().selectedField(selector))
}
