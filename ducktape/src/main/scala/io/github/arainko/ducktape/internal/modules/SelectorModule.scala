package io.github.arainko.ducktape.internal.modules

import scala.quoted.*
import scala.deriving.*
import io.github.arainko.ducktape.function.*
import scala.util.NotGiven

private[internal] trait SelectorModule { self: Module & MirrorModule & FieldModule =>
  import quotes.reflect.*

  def selectedField[From: Type, FieldType](
    mirror: Expr[Mirror.ProductOf[From]],
    lambda: Expr[From => FieldType]
  ): String = {
    val validFields = Field.fromMirror(mirror).map(_.name)
    lambda match {
      case FieldSelector(fieldName) if validFields.contains(fieldName) => fieldName
      case other                                                           => 
        val suggestions = validFields.map(arg => s"'_.$arg'").mkString(", ")
        report.errorAndAbort(s"Not a field selector! Try one of these: $suggestions. $other")
    }
  }

  def selectedArg[NamedArgs <: Tuple: Type, ArgType](
    selector: Expr[FunctionArguments[NamedArgs] => ArgType]
  ): String = {
    val arguments = Field.fromNamedArguments[NamedArgs].map(_.name)
    selector.asTerm match {
      case ArgSelector(argumentName) if arguments.contains(argumentName) => argumentName
      case other                     => 
        val suggestions = arguments.map(arg => s"'_.$arg'").mkString(", ")
        report.errorAndAbort(s"Not an argument selector! Try one of these: $suggestions")
    }
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
    def unapply(arg: Expr[Any]): Option[String] =
      PartialFunction.condOpt(arg.asTerm) {
        case Lambda(_, Select(Ident(_), fieldName)) => fieldName
      }
  }

  object ArgSelector {
    def unapply(arg: Term): Option[String] =
      PartialFunction.condOpt(arg) {
        case Lambda(_, DynamicSelector(argumentName)) => argumentName
      }

    private object DynamicSelector {
      def unapply(arg: Term): Option[String] =
        PartialFunction.condOpt(arg.asExpr) {
          case '{ ($args: FunctionArguments[namedArgs]).selectDynamic($selectedArg) } => selectedArg.valueOrAbort
        }

    }
  }
}
