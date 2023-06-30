package io.github.arainko.ducktape

import scala.quoted.*
import io.github.arainko.ducktape.internal.modules.*
import scala.collection.IterableFactory
import scala.collection.Factory

enum Plan {
  def sourceTpe: Type[?]
  def destTpe: Type[?]

  final def traverse(paths: List[String | Type[?]])(using Quotes): Option[Plan] = {
    def recurse(current: Option[Plan], paths: List[String | Type[?]])(using Quotes): Option[Option[Plan]] =
      current.flatMap { plan =>
        paths match {
          case (head: String) :: next =>
            (PartialFunction.condOpt(plan) {
              case BetweenProducts(sourceTpe, destTpe, fieldPlans) =>
                recurse(fieldPlans.get(head), next)
            }).flatten
          case (head: Type[?]) :: next =>
            (PartialFunction.condOpt(plan) {
              case BetweenCoproducts(sourceTpe, destTpe, casePlans) =>
                println(s"head: ${head.repr.show}")
                val reprs = casePlans.find { (_, plan) => 
                  println(s"curr src: ${plan.sourceTpe.repr.show}")
                  println(s"curr dest: ${plan.destTpe.repr.show}")
                  println()

                  head.repr =:= plan.sourceTpe.repr
                }

                
                recurse(reprs.map(_._2), next)
            }).flatten
          case Nil => Some(Some(plan))
        }
      }

    recurse(Some(this), paths).flatten
  }

  case Upcast(sourceTpe: Type[?], destTpe: Type[?])
  case BetweenProducts(sourceTpe: Type[?], destTpe: Type[?], fieldPlans: Map[String, Plan])
  case BetweenCoproducts(sourceTpe: Type[?], destTpe: Type[?], casePlans: Map[String, Plan])
  case BetweenOptions(sourceTpe: Type[?], destTpe: Type[?], plan: Plan)
  case BetweenNonOptionOption(sourceTpe: Type[?], destTpe: Type[?], dest: Plan)
  case BetweenCollections(destCollectionTpe: Type[?], sourceTpe: Type[?], destTpe: Type[?], dest: Plan)
  case BetweenSingletons(sourceTpe: Type[?], destTpe: Type[?], expr: Expr[Any])
  case UserDefined(sourceTpe: Type[?], destTpe: Type[?], transformer: Expr[UserDefinedTransformer[?, ?]])
}

object Plan {
  def unapply(plan: Plan): (Type[?], Type[?]) = (plan.sourceTpe, plan.destTpe)
}
