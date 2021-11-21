package io.github.arainko.ducktape.internal

import scala.quoted.*
import io.github.arainko.ducktape.Field
import scala.deriving.Mirror
// import io.github.arainko.ducktape.internal.BuilderMacros.FieldSelect

object BuilderMacros {
  transparent inline def droppedWithLambda[
    From,
    FieldType,
    Fields <: Tuple
  ](inline lambda: From => FieldType) =
    ${ droppedWithLambdaImpl[From, FieldType, Fields]('lambda) }

  def droppedWithLambdaImpl[
    From: Type,
    FieldType: Type,
    Fields <: Tuple: Type
  ](lambda: Expr[From => FieldType])(using Quotes) =
    BuilderMacros().selectFromInlinedLambda[From, FieldType, Fields](lambda)

}

class BuilderMacros(using val quotes: Quotes) {
  import quotes.reflect.*

  def selectFromInlinedLambda[
    From,
    FieldType,
    Fields <: Tuple: Type
  ](lambda: Expr[From => FieldType]) = {
    given Printer[Tree] = Printer.TreeStructure
    println(lambda.asTerm.show)
    val selectedFieldName = lambda.asTerm match {
      case FieldSelector(fieldName) => fieldName
      case _                        => report.errorAndAbort("Not a field selector!")
    }
    val fieldNameType = ConstantType(StringConstant(selectedFieldName)).asType
    val droppedType = fieldNameType match {
      case '[field] => Type.of[Field.DropByLabel[field, Fields]]
    }
    droppedType match {
      case '[dropped] => '{ ??? : dropped }
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

    def unapply(arg: Term) =
      arg match {
        case SelectorLambda(_, Select(Ident(_), fieldName)) => Some(fieldName)
        case _                                              => None
      }
  end FieldSelector

}
