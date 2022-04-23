package io.github.arainko.ducktape.internal.modules

import scala.quoted.*
import io.github.arainko.ducktape.function.*
import scala.util.NotGiven

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

  def selectedArg[NamedArgs <: Tuple: Type, ArgType](
    selector: Expr[FunctionArguments[NamedArgs] => ArgType]
  ): String = {
    val arguments = argNames[NamedArgs]
    selector.asTerm match {
      case ArgSelector(argumentName) if arguments.contains(argumentName) => argumentName
      case other                     => 
        val suggestions = arguments.map(arg => s"'_.$arg'").mkString(", ")
        report.errorAndAbort(s"Not an argument selector! Try one of these: $suggestions")
    }
  }

  def caseOrdinal[From: Type, Case <: From: Type](using From: DerivingMirror.SumOf[From]): Int = {
    val caseRepr = TypeRepr.of[Case]
    val cases = Case.fromMirror(From)
    cases.find(c => c.tpe =:= caseRepr).getOrElse(report.errorAndAbort("Not a case!")).ordinal
  }

  private def argNames[NamedArgs <: Tuple: Type]: List[String] =
    Type.of[NamedArgs] match {
      case '[EmptyTuple] => List.empty
      case '[NamedArgument[name, ?] *: tail] => 
        Type.valueOfConstant[name].getOrElse(report.errorAndAbort("Not a constant named arg name")) :: argNames[tail]
    } 

  object SelectorLambda {
    def unapply(arg: Term): Option[(List[ValDef], Term)] =
      arg match {
        case Inlined(_, _, Lambda(vals, term)) => Some((vals, term))
        case Inlined(_, _, nested)             => SelectorLambda.unapply(nested)
        case _                                 => None
      }
  }

  object FieldSelector {
    def unapply(arg: Term): Option[String] =
      PartialFunction.condOpt(arg) {
        case SelectorLambda(_, Select(Ident(_), fieldName)) => fieldName
      }
  }

  object ArgSelector {
    def unapply(arg: Term): Option[String] =
      PartialFunction.condOpt(arg) {
        case SelectorLambda(_, DynamicSelector(argumentName)) =>
          argumentName
      }

    private object DynamicSelector {
      def unapply(arg: Term): Option[String] =
        PartialFunction.condOpt(arg) {
          case Apply(Select(Ident(_), "selectDynamic"), List(Literal(StringConstant(argumentName)))) => argumentName
        }

    }
  }
}
