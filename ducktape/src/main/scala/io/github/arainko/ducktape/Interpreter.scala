package io.github.arainko.ducktape

import io.github.arainko.ducktape.internal.modules.*
import scala.quoted.*
import scala.annotation.nowarn
import scala.collection.Factory

object Interpreter {

  inline def transformPlanned[A, B](value: A) = ${ createTransformation[A, B]('value) }

  def createTransformation[A: Type, B: Type](value: Expr[A])(using Quotes): Expr[B] = {
    import quotes.reflect.*
    val plan = Planner.createPlan[A, B]
    recurse(plan, value).asExprOf[B]
  }

  private def recurse(plan: Plan, value: Expr[Any])(using Quotes): Expr[Any] = {
    import quotes.reflect.*

    plan match {
      case Plan.Upcast(_, _) =>
        value

      case Plan.BetweenProducts(sourceTpe, destTpe, fieldPlans) =>
        val args = fieldPlans.map { (fieldName, plan) =>
          val fieldValue = value.accessFieldByName(fieldName).asExpr
          NamedArg(fieldName, recurse(plan, fieldValue).asTerm)
        }
        Constructor(destTpe.repr).appliedToArgs(args.toList).asExpr

      case Plan.BetweenCoproducts(sourceTpe, destTpe, casePlans) =>
        ???

      case Plan.BetweenOptions(sourceTpe, destTpe, plan) =>
        (sourceTpe -> destTpe) match {
          case '[src] -> '[dest] =>
            val optionValue = value.asExprOf[Option[src]]
            def transformation(value: Expr[src])(using Quotes): Expr[dest] = recurse(plan, value).asExprOf[dest]
            '{ $optionValue.map(src => ${ transformation('src) }) }
        }

      case Plan.BetweenNonOptionOption(sourceTpe, destTpe, plan) =>
        (sourceTpe -> destTpe) match { case '[src] -> '[dest] =>
          val sourceValue = value.asExprOf[src]
          def transformation(value: Expr[src])(using Quotes): Expr[dest] = recurse(plan, value).asExprOf[dest]
          '{ Some(${ transformation(sourceValue) }) }
        }

      case Plan.BetweenCollections(destCollectionTpe, sourceTpe, destTpe, plan) =>
        (destCollectionTpe, sourceTpe, destTpe) match { case ('[destCollTpe], '[srcElem], '[destElem]) =>
          val sourceValue = value.asExprOf[Iterable[srcElem]]
          val factory = Expr.summon[Factory[destElem, destCollTpe]].get
          def transformation(value: Expr[srcElem])(using Quotes): Expr[destElem] = recurse(plan, value).asExprOf[destElem]
          '{ $sourceValue.map(src => ${ transformation('src) }).to($factory) }
        }

      case Plan.BetweenSingletons(sourceTpe, destTpe, expr) =>
        expr

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
