package io.github.arainko.ducktape

import io.github.arainko.ducktape.internal.modules.*

import scala.annotation.nowarn
import scala.collection.Factory
import scala.quoted.*
import scala.annotation.tailrec

object Interpreter {

  inline def transformPlanned[A, B](value: A, inline configs: Config[A, B]*) = ${ createTransformation[A, B]('value, 'configs) }

  def createTransformation[A: Type, B: Type](value: Expr[A], configs: Expr[Seq[Config[A, B]]])(using Quotes): Expr[B] = {
    import quotes.reflect.*

    val plan = Planner.createPlan[A, B]
    val config = Configuration.parse(configs)
    val reconfiguredPlan = config.foldLeft(plan) {
      case (plan, Configuration.At(path, target, config)) =>
        plan.replaceAt(path, target)(config)
    }
    println(s"OG PLAN: ${plan.show}")
    println(s"CONFIG: $config")
    println(s"CONF PLAN: ${reconfiguredPlan.show}")
    println()
    unsafeRefinePlan(reconfiguredPlan) match {
      case Left(errors) =>
        val rendered = errors.map(err => s"${err.message} @ ${err.context.render}").mkString("\n")
        report.errorAndAbort(rendered)
      case Right(totalPlan) =>
        recurse(totalPlan, value).asExprOf[B]
    }
  }

  private def unsafeRefinePlan[A <: Plan.Error](plan: Plan[A]): Either[List[Plan.Error], Plan[Nothing]] = {

    @tailrec
    def recurse(stack: List[Plan[A]], errors: List[Plan.Error]): List[Plan.Error] =
      stack match {
        case head :: next =>
          head match {
            case plan: Plan.Upcast => recurse(next, errors)
            case Plan.BetweenProducts(_, _, fieldPlans) =>
              recurse(fieldPlans.values.toList ::: next, errors)
            case Plan.BetweenCoproducts(_, _, casePlans) =>
              recurse(casePlans.values.toList ::: next, errors)
            case Plan.BetweenOptions(_, _, plan)         => recurse(plan :: next, errors)
            case Plan.BetweenNonOptionOption(_, _, plan) => recurse(plan :: next, errors)
            case Plan.BetweenCollections(_, _, _, plan)  => recurse(plan :: next, errors)
            case plan: Plan.BetweenSingletons            => recurse(next, errors)
            case plan: Plan.UserDefined                  => recurse(next, errors)
            case plan: Plan.Derived                      => recurse(next, errors)
            case plan: Plan.Configured                   => recurse(next, errors)
            case plan: Plan.BetweenWrappedUnwrapped      => recurse(next, errors)
            case plan: Plan.BetweenUnwrappedWrapped      => recurse(next, errors)
            case error: Plan.Error                       => recurse(next, error :: errors)
          }
        case Nil => errors
        }
    val errors = recurse(plan :: Nil, Nil)
    // if no errors were accumulated that means there are no Plan.Error nodes which means we operate on a Plan[Nothing]
    Either.cond(errors.isEmpty, plan.asInstanceOf[Plan[Nothing]], errors)
  }

  private def recurse(plan: Plan[Nothing], value: Expr[Any])(using Quotes): Expr[Any] = {
    import quotes.reflect.*

    plan match {
      case Plan.Upcast(_, _) => value

      case Plan.Configured(_, _, config) =>
        config match {
          case Configuration.Const(value) => value
        }

      case Plan.BetweenProducts(sourceTpe, destTpe, fieldPlans) =>
        val args = fieldPlans.map {
          case (fieldName, p: Plan.Configured) =>
            NamedArg(fieldName, recurse(p, value).asTerm)
          case (fieldName, plan) =>
            val fieldValue = value.accessFieldByName(fieldName).asExpr
            NamedArg(fieldName, recurse(plan, fieldValue).asTerm)
        }
        Constructor(destTpe.repr).appliedToArgs(args.toList).asExpr

      case Plan.BetweenCoproducts(sourceTpe, destTpe, casePlans) =>
        val branches = casePlans
          .map { (_, plan) =>
            (plan.sourceTpe -> plan.destTpe) match {
              case '[src] -> '[dest] =>
                val sourceValue = '{ $value.asInstanceOf[src] }
                IfBranch(IsInstanceOf(value, sourceTpe), recurse(plan, sourceValue))
            }
          }
          .toList
        ifStatement(branches).asExpr

      case Plan.BetweenOptions(sourceTpe, destTpe, plan) =>
        (sourceTpe -> destTpe) match {
          case '[src] -> '[dest] =>
            val optionValue = value.asExprOf[Option[src]]
            def transformation(value: Expr[src])(using Quotes): Expr[dest] = recurse(plan, value).asExprOf[dest]
            '{ $optionValue.map(src => ${ transformation('src) }) }
        }

      case Plan.BetweenNonOptionOption(sourceTpe, destTpe, plan) =>
        (sourceTpe -> destTpe) match {
          case '[src] -> '[dest] =>
            val sourceValue = value.asExprOf[src]
            def transformation(value: Expr[src])(using Quotes): Expr[dest] = recurse(plan, value).asExprOf[dest]
            '{ Some(${ transformation(sourceValue) }) }
        }

      case Plan.BetweenCollections(destCollectionTpe, sourceTpe, destTpe, plan) =>
        (destCollectionTpe, sourceTpe, destTpe) match {
          case ('[destCollTpe], '[srcElem], '[destElem]) =>
            val sourceValue = value.asExprOf[Iterable[srcElem]]
            val factory = Expr.summon[Factory[destElem, destCollTpe]].get //TODO: Make it nicer
            def transformation(value: Expr[srcElem])(using Quotes): Expr[destElem] = recurse(plan, value).asExprOf[destElem]
            '{ $sourceValue.map(src => ${ transformation('src) }).to($factory) }
        }

      case Plan.BetweenSingletons(sourceTpe, destTpe, expr) => expr

      case Plan.BetweenWrappedUnwrapped(sourceTpe, destTpe, fieldName) =>
        value.accessFieldByName(fieldName).asExpr

      case Plan.BetweenUnwrappedWrapped(sourceTpe, destTpe) =>
        Constructor(destTpe.repr).appliedTo(value.asTerm).asExpr

      case Plan.UserDefined(source, dest, transformer) =>
        transformer match {
          case '{ $t: UserDefinedTransformer[src, dest] } =>
            val sourceValue = value.asExprOf[src]
            '{ $t.transform($sourceValue) }
        }

      case Plan.Derived(source, dest, transformer) =>
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
