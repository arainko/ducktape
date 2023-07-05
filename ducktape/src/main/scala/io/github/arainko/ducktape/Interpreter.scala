package io.github.arainko.ducktape

import io.github.arainko.ducktape.Plan.UserDefined
import io.github.arainko.ducktape.internal.modules.*

import scala.annotation.nowarn
import scala.collection.Factory
import scala.collection.mutable.{ Builder, ListBuffer }
import scala.quoted.*

object Interpreter {

  inline def transformPlanned[A, B](value: A) = ${ createTransformation[A, B]('value) }

  private def unsafeRefinePlan[A <: Plan.Error](plan: Plan[A]): Either[List[Plan.Error], Plan[Nothing]] = {
    def recurse(errors: Builder[Plan.Error, List[Plan.Error]], curr: Plan[A]): Unit =
      curr match {
        case plan: Plan.Upcast => ()
        case Plan.BetweenProducts(_, _, fieldPlans) =>
          fieldPlans.values.foreach(plan => recurse(errors, plan))
        case Plan.BetweenCoproducts(_, _, casePlans) =>
          casePlans.values.foreach(plan => recurse(errors, plan))
        case Plan.BetweenOptions(_, _, plan)         => recurse(errors, plan)
        case Plan.BetweenNonOptionOption(_, _, plan) => recurse(errors, plan)
        case Plan.BetweenCollections(_, _, _, plan)  => recurse(errors, plan)
        case plan: Plan.BetweenSingletons            => ()
        case plan: UserDefined                       => ()
        case plan: Plan.BetweenWrappedUnwrapped      => ()
        case plan: Plan.BetweenUnwrappedWrapped      => ()
        case error: Plan.Error                       => errors += error
      }
    val builder = List.newBuilder[Plan.Error]
    recurse(builder, plan)
    val errors = builder.result()
    // if no errors were accumulated that means there are no Plan.Error nodes which means we operate on a Plan[Nothing]
    Either.cond(errors.isEmpty, plan.asInstanceOf[Plan[Nothing]], errors)
  }

  def createTransformation[A: Type, B: Type](value: Expr[A])(using Quotes): Expr[B] = {
    import quotes.reflect.*

    val plan = Planner.createPlan[A, B]
    unsafeRefinePlan(plan) match {
      case Left(errors) =>
        val rendered = errors.map(err => s"${err.message} @ ${err.context.render}").mkString("\n")
        report.errorAndAbort(rendered)
      case Right(totalPlan) =>
        recurse(totalPlan, value).asExprOf[B]
    }
  }

  private def recurse(plan: Plan[Nothing], value: Expr[Any])(using Quotes): Expr[Any] = {
    import quotes.reflect.*

    plan match {
      case Plan.Upcast(_, _) => value

      case Plan.BetweenProducts(sourceTpe, destTpe, fieldPlans) =>
        val args = fieldPlans.map { (fieldName, plan) =>
          val fieldValue = value.accessFieldByName(fieldName).asExpr
          NamedArg(fieldName, recurse(plan, fieldValue).asTerm)
        }
        Constructor(destTpe.repr).appliedToArgs(args.toList).asExpr

      case Plan.BetweenCoproducts(sourceTpe, destTpe, casePlans) =>
        val branches = casePlans
          .map((_, plan) =>
            (plan.sourceTpe -> plan.destTpe) match {
              case '[src] -> '[dest] =>
                val sourceValue = '{ $value.asInstanceOf[src] }
                IfBranch(IsInstanceOf(value, sourceTpe), recurse(plan, sourceValue))
            }
          )
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
            val factory = Expr.summon[Factory[destElem, destCollTpe]].get
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
