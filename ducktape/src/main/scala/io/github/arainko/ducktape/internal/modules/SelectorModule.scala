package io.github.arainko.ducktape.internal.modules

import scala.quoted.*

private[internal] trait SelectorModule { self: Module & MirrorModule & FieldModule =>
  import quotes.reflect.*

  def selectedField[From: Type, FieldType](
    lambda: Expr[From => FieldType]
  )(using From: DerivingMirror.ProductOf[From]): String = {
    val validFields = Field.fromMirror(From).map(_.name)
    lambda.asTerm match {
      case FieldSelector(fieldName) if validFields.contains(fieldName) => fieldName
      case _                                                           => report.errorAndAbort("Not a field selector!")
    }
  }

  def caseOrdinal[From: Type, Case <: From: Type](using From: DerivingMirror.SumOf[From]): Int = {
    val caseRepr = TypeRepr.of[Case]
    val cases = Case.fromMirror(From)
    cases.find(c => c.tpe =:= caseRepr).getOrElse(report.errorAndAbort("Not a case!")).ordinal
  }

  object FieldSelector:
    object SelectorLambda:
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
