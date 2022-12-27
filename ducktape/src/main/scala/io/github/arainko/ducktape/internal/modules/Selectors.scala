package io.github.arainko.ducktape.internal.modules

import scala.quoted.*
import io.github.arainko.ducktape.internal.modules.common.*
import io.github.arainko.ducktape.function.FunctionArguments

object Selectors {
  def fieldName[From: Type, FieldType](
    validFields: Fields,
    selector: Expr[From => FieldType]
  )(using Quotes): String = {
    selector match {
      case FieldSelector(fieldName) if validFields.containsFieldWithName(fieldName) =>
        fieldName
      case other =>
        Failure.abort(Failure.InvalidFieldSelector(other, summon, Suggestion.fromFields(validFields)))
    }
  }

  def argName[ArgType: Type, ArgSelector <: FunctionArguments](
    validArgs: Fields,
    selector: Expr[ArgSelector => ArgType]
  )(using Quotes): String = {
    import quotes.reflect.*

    selector.asTerm match {
      case ArgSelector(argumentName) if validArgs.containsFieldWithName(argumentName) =>
        argumentName
      case ArgSelector(argumentName) =>
        Failure.abort(Failure.InvalidArgSelector.NotFound(selector, argumentName, Suggestion.fromFields(validArgs)))
      case other =>
        Failure.abort(Failure.InvalidArgSelector.NotAnArgSelector(selector, Suggestion.fromFields(validArgs)))
    }
  }

  object FunctionLambda {
    def unapply(using Quotes)(arg: quotes.reflect.Term): Option[(List[quotes.reflect.ValDef], quotes.reflect.Term)] = {
      import quotes.reflect.*

      arg match {
        case Inlined(_, _, Lambda(vals, term)) => Some(vals -> term)
        case Inlined(_, _, nested)             => FunctionLambda.unapply(nested)
        case _                                 => None
      }
    }
  }

  private object FieldSelector {
    def unapply(arg: Expr[Any])(using Quotes): Option[String] = {
      import quotes.reflect.*

      PartialFunction.condOpt(arg.asTerm) {
        case Lambda(_, Select(Ident(_), fieldName)) => fieldName
      }
    }
  }

  private object ArgSelector {
    def unapply(using Quotes)(arg: quotes.reflect.Term): Option[String] = {
      import quotes.reflect.*

      PartialFunction.condOpt(arg) {
        case Lambda(_, FunctionArgumentSelector(argumentName)) => argumentName
      }
    }
  }

  private object FunctionArgumentSelector {
    def unapply(using Quotes)(arg: quotes.reflect.Term): Option[String] =
      PartialFunction.condOpt(arg.asExpr) {
        case '{
              type argSelector <: FunctionArguments
              ($args: `argSelector`).selectDynamic($selectedArg).$asInstanceOf$[tpe]
            } =>
          selectedArg.valueOrAbort
      }
  }
}
