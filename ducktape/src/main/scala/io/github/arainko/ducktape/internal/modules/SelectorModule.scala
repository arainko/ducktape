package io.github.arainko.ducktape.internal.modules

import scala.quoted.*
import scala.deriving.*
import io.github.arainko.ducktape.function.*
import scala.util.NotGiven

private[internal] trait SelectorModule { self: Module & MirrorModule & FieldModule =>
  import quotes.reflect.*

  object Selectors {
    def fieldName[From: Type, FieldType](
      validFields: Fields,
      selector: Expr[From => FieldType]
    ): String =
      selector match {
        case FieldSelector(fieldName) if validFields.containsFieldWithName(fieldName) =>
          fieldName
        case other =>
          abort(Failure.InvalidFieldSelector(other, TypeRepr.of[From], Suggestion.fromFields(validFields)))
      }

    def argName[NamedArgs <: Tuple: Type, ArgType: Type](
      validArgs: Fields,
      selector: Expr[FunctionArguments[NamedArgs] => ArgType]
    ): String =
      selector.asTerm match {
        case ArgSelector(argumentName) if validArgs.containsFieldWithName(argumentName) =>
          argumentName
        case ArgSelector(argumentName) =>
          abort(Failure.InvalidArgSelector.NotFound(selector, argumentName, Suggestion.fromFields(validArgs)))
        case other =>
          abort(Failure.InvalidArgSelector.NotAnArgSelector(selector, Suggestion.fromFields(validArgs)))
      }

  }

  object FunctionLambda {
    def unapply(arg: Term): Option[(List[ValDef], Term)] =
      arg match {
        case Inlined(_, _, Lambda(vals, term)) => Some((vals, term))
        case Inlined(_, _, nested)             => FunctionLambda.unapply(nested)
        case _                                 => None
      }
  }

  private object FieldSelector {
    def unapply(arg: Expr[Any]): Option[String] =
      PartialFunction.condOpt(arg.asTerm) {
        case Lambda(_, Select(Ident(_), fieldName)) => fieldName
      }
  }

  private object ArgSelector {
    def unapply(arg: Term): Option[String] =
      PartialFunction.condOpt(arg) {
        case Lambda(_, DynamicSelector(argumentName)) => argumentName
      }
  }

  private object DynamicSelector {
    def unapply(arg: Term): Option[String] =
      PartialFunction.condOpt(arg.asExpr) {
        case '{ ($args: FunctionArguments[namedArgs]).selectDynamic($selectedArg) } => selectedArg.valueOrAbort
      }
  }
}
