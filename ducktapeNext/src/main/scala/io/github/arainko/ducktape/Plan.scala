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
      case Derived(sourceTpe, destTpe, transformer) =>
        s"Derived(${sourceTpe.show}, ${destTpe.show}, Transformer[...])"
      case Configured(sourceTpe, destTpe, config) =>
        s"Configured(${sourceTpe.show}, ${destTpe.show}, $config)"
      case PlanError(sourceTpe, destTpe, context, message) =>
        s"Error(${sourceTpe.show}, ${destTpe.show}, ${context.render}, ${message})"
      case BetweenUnwrappedWrapped(sourceTpe, destTpe) =>
        s"BetweenUnwrappedWrapped(${sourceTpe.show}, ${destTpe.show})"
      case BetweenWrappedUnwrapped(sourceTpe, destTpe, fieldName) =>
        s"BetweenWrappedUnwrapped(${sourceTpe.show}, ${destTpe.show}, $fieldName)"
    }
  }

  final def replaceAt[EE >: E <: Plan.Error](
    path: Path
  )(update: Plan[EE])(using Quotes): Option[Plan[EE]] = {
    // TODO: Clean this up, make it tailrec
    def recurse(current: Option[Plan[E]], segments: List[Path.Segment])(using Quotes): Option[Plan[EE]] =
      current.flatMap { plan =>
        segments match {
          case Path.Segment.Field(fieldName) :: tail =>
            val result = PartialFunction.condOpt(plan) {
              case plan @ BetweenProducts(sourceTpe, destTpe, fieldPlans) =>
                recurse(fieldPlans.get(fieldName), tail)
                  .map(fieldPlan => plan.copy(fieldPlans = fieldPlans.updated(fieldName, fieldPlan)))
            }
            result.flatten
          case Path.Segment.Case(tpe) :: tail =>
            val result = PartialFunction.condOpt(plan) {
              case plan @ BetweenCoproducts(sourceTpe, destTpe, casePlans) =>
                for {
                  (name, ogCasePlan) <- casePlans.collectFirst {
                    case (name, plan) if tpe.repr =:= plan.sourceTpe.repr => name -> plan
                  }
                  casePlan <- recurse(Some(ogCasePlan), tail)
                } yield plan.copy(casePlans = casePlans.updated(name, casePlan))
            }
            result.flatten
          case Nil => Some(update)
        }
      }

    recurse(Some(this), path.toList)
  }

  case Upcast(sourceTpe: Type[?], destTpe: Type[?]) extends Plan[Nothing]
  case UserDefined(sourceTpe: Type[?], destTpe: Type[?], transformer: Expr[UserDefinedTransformer[?, ?]]) extends Plan[Nothing]
  case Derived(sourceTpe: Type[?], destTpe: Type[?], transformer: Expr[Transformer2[?, ?]]) extends Plan[Nothing]
  case Configured(sourceTpe: Type[?], destTpe: Type[?], config: Plan.Configuration) extends Plan[Nothing]
  case BetweenUnwrappedWrapped(sourceTpe: Type[?], destTpe: Type[?]) extends Plan[Nothing]
  case BetweenWrappedUnwrapped(sourceTpe: Type[?], destTpe: Type[?], fieldName: String) extends Plan[Nothing]
  case BetweenSingletons(sourceTpe: Type[?], destTpe: Type[?], expr: Expr[Any]) extends Plan[Nothing]
  case BetweenProducts(sourceTpe: Type[?], destTpe: Type[?], fieldPlans: Map[String, Plan[E]]) extends Plan[E]
  case BetweenCoproducts(sourceTpe: Type[?], destTpe: Type[?], casePlans: Map[String, Plan[E]]) extends Plan[E]
  case BetweenOptions(sourceTpe: Type[?], destTpe: Type[?], plan: Plan[E]) extends Plan[E]
  case BetweenNonOptionOption(sourceTpe: Type[?], destTpe: Type[?], plan: Plan[E]) extends Plan[E]
  case BetweenCollections(destCollectionTpe: Type[?], sourceTpe: Type[?], destTpe: Type[?], plan: Plan[E]) extends Plan[E]
  // TODO: Use a well typed error definition here and not a String
  case Error(sourceTpe: Type[?], destTpe: Type[?], context: Plan.Context, message: String) extends Plan[Plan.Error]
}

object Plan {
  def unapply[E <: Plan.Error](plan: Plan[E]): (Type[?], Type[?]) = (plan.sourceTpe, plan.destTpe)

  final case class Context(rootSourceTpe: Type[?], rootDestTpe: Type[?], path: Path) {
    def add(segment: Path.Segment): Context = copy(path = path.appended(segment))
    def render(using Quotes): String = path.render
  }

  object Context {
    def empty(rootSourceTpe: Type[?], rootDestTpe: Type[?]): Context = Context(rootSourceTpe, rootDestTpe, Path.empty)
  }

  enum Configuration {
    case Const(value: Expr[Any])
  }

  def parseConfig[A: Type, B: Type](configs: Expr[Seq[Config[A, B]]])(using Quotes) = {
    import quotes.reflect.*

    val woooh = Varargs.unapply(configs).get
    woooh
      .map(_.asTerm)
      .map {
        case Apply(TypeApply(Select(Ident("Field2"), "const"), a :: b :: fieldTpe :: Nil), PathSelector(path) :: value :: Nil) =>
          path -> Plan.Configured(Type.of[Int], Type.of[Int], Plan.Configuration.Const(value.asExpr))
      }
  }
}
