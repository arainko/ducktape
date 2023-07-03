package io.github.arainko.ducktape

import scala.quoted.*
import io.github.arainko.ducktape.internal.modules.*
import scala.collection.IterableFactory
import scala.collection.Factory

sealed trait Plan[+E <: Plan.Error] {
  import Plan.*

  def sourceTpe: Type[?]
  def destTpe: Type[?]

  final def traverse(paths: List[String | Type[?]])(using Quotes): Option[Plan[E]] = {
    // TODO: Clean this up, make it tailrec
    def recurse(current: Option[Plan[E]], paths: List[String | Type[?]])(using Quotes): Option[Plan[E]] =
      current.flatMap { plan =>
        paths match {
          case (fieldName: String) :: next =>
            val result = PartialFunction.condOpt(plan) {
              case BetweenProducts(sourceTpe, destTpe, fieldPlans) =>
                recurse(fieldPlans.get(fieldName), next)
            }
            result.flatten
          case (tpe: Type[?]) :: next =>
            val result = PartialFunction.condOpt(plan) {
              case BetweenCoproducts(sourceTpe, destTpe, casePlans) =>
                val plan = casePlans.collectFirst { case (_, plan) if tpe.repr =:= plan.sourceTpe.repr => plan }
                recurse(plan, next)
            }
            result.flatten
          case Nil => Some(plan)
        }
      }

    recurse(Some(this), paths)
  }
}

object Plan {
  case class Upcast(sourceTpe: Type[?], destTpe: Type[?]) extends Plan[Nothing]

  case class BetweenProducts[+E <: Plan.Error](sourceTpe: Type[?], destTpe: Type[?], fieldPlans: Map[String, Plan[E]]) extends Plan[E]

  case class BetweenCoproducts[+E <: Plan.Error](sourceTpe: Type[?], destTpe: Type[?], casePlans: Map[String, Plan[E]]) extends Plan[E]

  case class BetweenOptions[+E <: Plan.Error](sourceTpe: Type[?], destTpe: Type[?], plan: Plan[E]) extends Plan[E]

  case class BetweenNonOptionOption[+E <: Plan.Error](sourceTpe: Type[?], destTpe: Type[?], plan: Plan[E]) extends Plan[E]

  case class BetweenCollections[+E <: Plan.Error](destCollectionTpe: Type[?], sourceTpe: Type[?], destTpe: Type[?], plan: Plan[E])
      extends Plan[E]

  case class BetweenSingletons(sourceTpe: Type[?], destTpe: Type[?], expr: Expr[Any]) extends Plan[Nothing]

  case class UserDefined(sourceTpe: Type[?], destTpe: Type[?], transformer: Expr[UserDefinedTransformer[?, ?]])
      extends Plan[Nothing]

  case class Error(sourceTpe: Type[?], destTpe: Type[?], context: Plan.Context, message: String)
      extends Plan[Plan.Error] // TODO: Use a well typed error definition here and not a String

  def unapply[E <: Plan.Error](plan: Plan[E]): (Type[?], Type[?]) = (plan.sourceTpe, plan.destTpe)

  final case class Context(paths: Vector[String | Type[?]]) {

    def add(segment: String | Type[?]): Context = copy(paths :+ segment)

    def render(using Quotes): String = {
      import quotes.reflect.*
      given Printer[TypeRepr] = Printer.TypeReprShortCode
      paths.map {
        case fieldName: String => fieldName
        case caseType: Type[?] => s"at[${caseType.repr.show}]"
      }.mkString("_.", ".", "")
    }
  }
}
