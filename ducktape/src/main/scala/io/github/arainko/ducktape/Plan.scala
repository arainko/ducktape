package io.github.arainko.ducktape

import scala.quoted.*
import io.github.arainko.ducktape.internal.modules.*
import scala.collection.IterableFactory
import scala.collection.Factory
import Plan.Error as PlanError //TODO: Why is this needed? I cannot refer to Plan.Error in bound of the Plan enum, lol
type PlanError = Plan.Error

enum Plan[+E <: PlanError] {
  import Plan.*

  def sourceTpe: Type[?]
  def destTpe: Type[?]

  final def show(using Quotes): String = {
    import quotes.reflect.*
    given (using q: Quotes): q.reflect.Printer[q.reflect.TypeRepr] = q.reflect.Printer.TypeReprShortCode
    extension (tpe: Type[?])(using Quotes) def show: String = tpe.repr.show

    this match {
      case Upcast(sourceTpe, destTpe) =>
        s"Upcast(${sourceTpe.show}, ${destTpe.show})"
      case BetweenProducts(sourceTpe, destTpe, fieldPlans) =>
        s"BetweenProducts(${sourceTpe.show}, ${destTpe.show}, ${fieldPlans.map((name, plan) => name -> plan.show)})"
      case BetweenCoproducts(sourceTpe, destTpe, casePlans) =>
        s"BetweenCoproducts(${sourceTpe.show}, ${destTpe.show}, ${casePlans.map((name, plan) => name -> plan.show)})"
      case BetweenOptions(sourceTpe, destTpe, plan) =>
        s"BetweenOptions(${sourceTpe.show}, ${destTpe.show}, ${plan.show})"
      case BetweenNonOptionOption(sourceTpe, destTpe, plan) =>
        s"BetweenNonOptionOption(${sourceTpe.show}, ${destTpe.show}, ${plan.show})"
      case BetweenCollections(destCollectionTpe, sourceTpe, destTpe, plan) =>
        s"BetweenCollections(${destCollectionTpe.show}, ${sourceTpe.show}, ${destTpe.show}, ${plan.show})"
      case BetweenSingletons(sourceTpe, destTpe, expr) =>
        s"BetweenSingletons(${sourceTpe.show}, ${destTpe.show}, Expr(...))"
      case UserDefined(sourceTpe, destTpe, transformer) =>
        s"UserDefined(${sourceTpe.show}, ${destTpe.show}, Transformer[...])"
      case PlanError(sourceTpe, destTpe, context, message) =>
        s"Error(${sourceTpe.show}, ${destTpe.show}, ${context.render}, ${message})"
      case BetweenUnwrappedWrapped(sourceTpe, destTpe) => 
        s"BetweenUnwrappedWrapped(${sourceTpe.show}, ${destTpe.show})"
      case BetweenWrappedUnwrapped(sourceTpe, destTpe, fieldName) =>  
        s"BetweenWrappedUnwrapped(${sourceTpe.show}, ${destTpe.show}, $fieldName)"
    }
  }

  final def updateAt[EE >: E <: Plan.Error](
    paths: List[String | Type[?]]
  )(update: Plan[EE] => Plan[EE])(using Quotes): Option[Plan[EE]] = {
    // TODO: Clean this up, make it tailrec
    def recurse(current: Option[Plan[E]], paths: List[String | Type[?]])(using Quotes): Option[Plan[EE]] =
      current.flatMap { plan =>
        paths match {
          case (fieldName: String) :: next =>
            val result = PartialFunction.condOpt(plan) {
              case plan @ BetweenProducts(sourceTpe, destTpe, fieldPlans) =>
                recurse(fieldPlans.get(fieldName), next)
                  .map(fieldPlan => plan.copy(fieldPlans = fieldPlans.updated(fieldName, fieldPlan)))
            }
            result.flatten

          case (tpe: Type[?]) :: next =>
            val result = PartialFunction.condOpt(plan) {
              case plan @ BetweenCoproducts(sourceTpe, destTpe, casePlans) =>
                for {
                  (name, ogCasePlan) <- casePlans.collectFirst {
                    case (name, plan) if tpe.repr =:= plan.sourceTpe.repr => name -> plan
                  }
                  casePlan <- recurse(Some(ogCasePlan), next)
                } yield plan.copy(casePlans = casePlans.updated(name, casePlan))
            }
            result.flatten
          case Nil => Some(update(plan))
        }
      }

    recurse(Some(this), paths)
  }

  case Upcast(sourceTpe: Type[?], destTpe: Type[?]) extends Plan[Nothing]
  case BetweenProducts(sourceTpe: Type[?], destTpe: Type[?], fieldPlans: Map[String, Plan[E]]) extends Plan[E]
  case BetweenCoproducts(sourceTpe: Type[?], destTpe: Type[?], casePlans: Map[String, Plan[E]]) extends Plan[E]
  case BetweenOptions(sourceTpe: Type[?], destTpe: Type[?], plan: Plan[E]) extends Plan[E]
  case BetweenNonOptionOption(sourceTpe: Type[?], destTpe: Type[?], plan: Plan[E]) extends Plan[E]
  case BetweenCollections(destCollectionTpe: Type[?], sourceTpe: Type[?], destTpe: Type[?], plan: Plan[E]) extends Plan[E]
  case BetweenSingletons(sourceTpe: Type[?], destTpe: Type[?], expr: Expr[Any]) extends Plan[Nothing]
  case UserDefined(sourceTpe: Type[?], destTpe: Type[?], transformer: Expr[UserDefinedTransformer[?, ?]]) extends Plan[Nothing]
  case BetweenUnwrappedWrapped(sourceTpe: Type[?], destTpe: Type[?]) extends Plan[Nothing]
  case BetweenWrappedUnwrapped(sourceTpe: Type[?], destTpe: Type[?], fieldName: String) extends Plan[Nothing]
  // TODO: Use a well typed error definition here and not a String
  case Error(sourceTpe: Type[?], destTpe: Type[?], context: Plan.Context, message: String) extends Plan[Plan.Error]
}

object Plan {
  def unapply[E <: Plan.Error](plan: Plan[E]): (Type[?], Type[?]) = (plan.sourceTpe, plan.destTpe)

  final case class Context(sourceTpe: Type[?], destTpe: Type[?], path: Vector[String | Type[?]]) {

    def add(segment: String | Type[?]): Context = copy(path = path :+ segment)

    def render(using Quotes): String = {
      import quotes.reflect.*
      given Printer[TypeRepr] = Printer.TypeReprShortCode
      path.map {
        case fieldName: String => fieldName
        case caseType: Type[?] => s"at[${caseType.repr.show}]"
      }.mkString("_.", ".", "")
    }
  }
}
