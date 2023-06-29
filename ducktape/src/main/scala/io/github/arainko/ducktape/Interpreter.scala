package io.github.arainko.ducktape

import io.github.arainko.ducktape.internal.modules.*
import scala.quoted.*

object Interpreter {
  def createTransformation[A: Type, B: Type](value: Expr[A])(using Quotes): Expr[B] = {
    val plan = Planner.createPlan[A, B]
    recurse[A, B](plan, value)
  }

  private def recurse[A: Type, B: Type](plan: Plan, value: Expr[A])(using Quotes): Expr[B] = {
    import quotes.reflect.*

    plan match {
      case Plan.Upcast(tpe) => 
        value.asExprOf[B]

      case Plan.FieldAccess(name, tpe, plan) =>
        tpe match { case '[a] => 
          val fieldValue = value.accessFieldByName(name).asExprOf[a]
          recurse[a, B](plan, fieldValue)
        }

      case Plan.CaseBranch(tpe, plan) =>
        ???

      case Plan.ProductTransformation(fieldPlans) =>
        val args = fieldPlans.map((fieldName, plan) => NamedArg(fieldName, recurse))
        Constructor(summon[Type[A]].repr)
        ???
        
      case Plan.CoproductTransformation(casePlans) =>
        ???

      case Plan.BetweenOptionsTransformation(plan) =>
        ???

      case Plan.WrapInOptionTransformation(dest) =>
        ???

      case Plan.BetweenCollectionsTransformation(dest) =>
        ???

      case Plan.SingletonTransformation(tpe, expr) =>
        ???

      case Plan.TransformerTransformation(source, dest, transformation) =>
        ???
    }
  }

}
