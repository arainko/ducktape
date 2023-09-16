package io.github.arainko.ducktape

import io.github.arainko.ducktape.internal.modules.*
import io.github.arainko.ducktape.internal.*
import io.github.arainko.ducktape.{ Case2 as CaseConfig, Field2 as FieldConfig }

import scala.annotation.nowarn
import scala.collection.Factory
import scala.quoted.*
import scala.annotation.tailrec

object PlanInterpreter {

  transparent inline def transformVia[A, B, Args <: FunctionArguments](
    value: A,
    inline function: Any,
    inline configs: Arg2[A, B, Args] | CaseConfig[A, B]*
  ) = ${ createTransformationVia('value, 'function, 'configs) }

  def createTransformationVia[A: Type, B: Type, Args <: FunctionArguments: Type](
    value: Expr[A],
    function: Expr[Any],
    configs: Expr[Seq[Arg2[A, B, Args] | CaseConfig[A, B]]]
  )(using Quotes) = {
    import quotes.reflect.*

    Planner.betweenTypeAndFunction[A](function).refine match {
      case Left(errors) =>
        val rendered = errors.map(err => s"${err.message} @ ${err.sourceContext.render}").mkString("\n")
        report.errorAndAbort(rendered)
      case Right(totalPlan) =>
        recurse(totalPlan, value)
    }
  }

  inline def transformBetween[A, B](
    value: A,
    inline configs: FieldConfig[A, B] | CaseConfig[A, B]*
  ) = ${ createTransformationBetween[A, B]('value, 'configs) }

  def createTransformationBetween[A: Type, B: Type](
    value: Expr[A],
    configs: Expr[Seq[FieldConfig[A, B] | CaseConfig[A, B]]]
  )(using Quotes): Expr[B] = {
    import quotes.reflect.*

    val plan = Planner.betweenTypes[A, B]
    val config = Configuration.parse(configs)
    val reconfiguredPlan = config.foldLeft(plan) { (plan, config) => plan.configure(config) }
    println(s"OG PLAN: ${plan.show}")
    println()
    println(s"CONFIG: ${Debug.show(config)}")
    println()
    println(s"CONF PLAN: ${reconfiguredPlan.show}")
    println()
    reconfiguredPlan.refine match {
      case Left(errors) =>
        val rendered = errors.map(err => s"${err.message} @ ${err.sourceContext.render}").mkString("\n")
        report.errorAndAbort(rendered)
      case Right(totalPlan) =>
        recurse(totalPlan, value).asExprOf[B]
    }
  }

  private def recurse(plan: Plan[Nothing], value: Expr[Any])(using Quotes): Expr[Any] = {
    import quotes.reflect.*

    plan match {
      case Plan.Upcast(_, _, _, _) => value

      case Plan.Configured(_, _, _, _, config) =>
        config match {
          case Configuration.Const(value) => value
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
            val factory = Expr.summon[Factory[destElem, destCollTpe]].get // TODO: Make it nicer
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
          case '{ $t: UserDefinedTransformer[src, dest] } =>
            val sourceValue = value.asExprOf[src]
            '{ $t.transform($sourceValue) }
        }

      case Plan.Derived(source, dest, _, _, transformer) =>
        transformer match {
          case '{ $t: Transformer2[src, dest] } =>
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
