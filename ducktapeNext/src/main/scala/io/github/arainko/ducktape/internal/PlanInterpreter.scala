package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.internal.*

import scala.collection.Factory
import scala.quoted.*

private[ducktape] object PlanInterpreter {

  def run[A: Type](plan: Plan[Nothing], sourceValue: Expr[A])(using Quotes): Expr[Any] =
    recurse(plan, sourceValue)(using sourceValue)

  private def recurse[A: Type](plan: Plan[Nothing], value: Expr[Any])(using toplevelValue: Expr[A])(using Quotes): Expr[Any] = {
    import quotes.reflect.*

    plan match {
      case Plan.Upcast(_, _, _, _) => value

      case Plan.Configured(_, _, _, _, config) =>
        config match {
          case Configuration.Const(value, _) =>
            value
          case Configuration.CaseComputed(_, function) =>
            '{ $function.apply($value) }
          case Configuration.FieldComputed(_, function) =>
            '{ $function.apply($toplevelValue) }
          case Configuration.FieldReplacement(source, name, tpe) =>
            source.accessFieldByName(name).asExpr
        }

      case Plan.BetweenProducts(sourceTpe, destTpe, _, _, fieldPlans) =>
        val args = fieldPlans.map {
          case (fieldName, p: Plan.Configured) =>
            NamedArg(fieldName, recurse(p, value).asTerm)
          case (fieldName, plan) =>
            val fieldValue = value.accessFieldByName(fieldName).asExpr
            NamedArg(fieldName, recurse(plan, fieldValue).asTerm)
        }
        Constructor(destTpe.repr).appliedToArgs(args.toList).asExpr

      case Plan.BetweenCoproducts(sourceTpe, destTpe, _, _, casePlans) =>
        val branches = casePlans.map { plan =>
          (plan.sourceTpe -> plan.destTpe) match {
            case '[src] -> '[dest] =>
              val sourceValue = '{ $value.asInstanceOf[src] }
              IfBranch(IsInstanceOf(value, plan.sourceTpe), recurse(plan, sourceValue))
          }
        }.toList
        ifStatement(branches).asExpr

      case Plan.BetweenProductFunction(sourceTpe, destTpe, _, _, argPlans, function) =>
        val args = argPlans.map {
          case (fieldName, p: Plan.Configured) =>
            recurse(p, value).asTerm
          case (fieldName, plan) =>
            val fieldValue = value.accessFieldByName(fieldName).asExpr
            recurse(plan, fieldValue).asTerm
        }
        function.appliedTo(args.toList)

      case Plan.BetweenOptions(sourceTpe, destTpe, _, _, plan) =>
        (sourceTpe -> destTpe) match {
          case '[src] -> '[dest] =>
            val optionValue = value.asExprOf[Option[src]]
            def transformation(value: Expr[src])(using Quotes): Expr[dest] = recurse(plan, value).asExprOf[dest]
            '{ $optionValue.map(src => ${ transformation('src) }) }
        }

      case Plan.BetweenNonOptionOption(sourceTpe, destTpe, _, _, plan) =>
        (sourceTpe -> destTpe) match {
          case '[src] -> '[dest] =>
            val sourceValue = value.asExprOf[src]
            def transformation(value: Expr[src])(using Quotes): Expr[dest] = recurse(plan, value).asExprOf[dest]
            '{ Some(${ transformation(sourceValue) }) }
        }

      case Plan.BetweenCollections(destCollectionTpe, sourceTpe, destTpe, _, _, plan) =>
        (destCollectionTpe, sourceTpe, destTpe) match {
          case ('[destCollTpe], '[srcElem], '[destElem]) =>
            val sourceValue = value.asExprOf[Iterable[srcElem]]
            // TODO: Make it nicer, move this into Planner since we cannot be sure that a facotry exists
            val factory = Expr.summon[Factory[destElem, destCollTpe]].get
            def transformation(value: Expr[srcElem])(using Quotes): Expr[destElem] = recurse(plan, value).asExprOf[destElem]
            '{ $sourceValue.map(src => ${ transformation('src) }).to($factory) }
        }

      case Plan.BetweenSingletons(sourceTpe, destTpe, _, _, expr) => expr

      case Plan.BetweenWrappedUnwrapped(sourceTpe, destTpe, _, _, fieldName) =>
        value.accessFieldByName(fieldName).asExpr

      case Plan.BetweenUnwrappedWrapped(sourceTpe, destTpe, _, _) =>
        Constructor(destTpe.repr).appliedTo(value.asTerm).asExpr

      case Plan.UserDefined(source, dest, _, _, transformer) =>
        transformer match {
          case '{ $t: Transformer[src, dest] } =>
            val sourceValue = value.asExprOf[src]
            '{ $t.transform($sourceValue) }
        }

      case Plan.Derived(source, dest, _, _, transformer) =>
        transformer match {
          case '{ $t: Transformer.Derived[src, dest] } =>
            val sourceValue = value.asExprOf[src]
            '{ $t.transform($sourceValue) }
        }
    }
  }

  private def ifStatement(using Quotes)(branches: List[IfBranch]): quotes.reflect.Term = {
    import quotes.reflect.*

    branches match {
      case IfBranch(cond, value) :: xs =>
        If(cond.asTerm, value.asTerm, ifStatement(xs))
      case Nil =>
        '{ throw RuntimeException("Unhandled condition encountered during Coproduct Transformer derivation") }.asTerm
    }
  }

  private def IsInstanceOf(value: Expr[Any], tpe: Type[?])(using Quotes) =
    tpe match {
      case '[tpe] => '{ $value.isInstanceOf[tpe] }
    }

  private case class IfBranch(cond: Expr[Boolean], value: Expr[Any])

}
