package io.github.arainko.ducktape

import scala.quoted.*
import io.github.arainko.ducktape.internal.modules.*

object Planner {
  import Structure.*

  inline def print[A, B] = ${ printMacro[A, B] }

  def printMacro[A: Type, B: Type](using Quotes): Expr[Unit] = {
    val plan = createPlan[A, B]
    quotes.reflect.report.info(plan.toString())
    '{}
  }

  def createPlan[Source: Type, Dest: Type](using Quotes): Plan = {
    val src = Structure.of[Source]
    val dest = Structure.of[Dest]
    val plan = recurse(src, dest)
    println(plan.traverse("opt" :: "int" :: Nil))
    plan
  }

  private def recurse(source: Structure, dest: Structure)(using Quotes): Plan =
    (source.force -> dest.force) match {
      case UserDefinedTransformation(transformer) =>
        Plan.UserDefined(source.tpe, dest.tpe, transformer)

      case (source, dest) if source.typeRepr <:< dest.typeRepr =>
        Plan.Upcast(source.tpe, dest.tpe)

      case Structure('[Option[srcTpe]], srcName) -> Structure('[Option[destTpe]], destName) =>
        Plan.BetweenOptions(Type[srcTpe], Type[destTpe], createPlan[srcTpe, destTpe])

      case Structure('[a], _) -> Structure('[Option[destTpe]], destName) =>
        Plan.BetweenNonOptionOption(Type[a], Type[destTpe], createPlan[a, destTpe])

      case Structure(source @ '[Iterable[srcTpe]], srcName) -> Structure(dest @ '[Iterable[destTpe]], destName) =>
        Plan.BetweenCollections(dest, Type[srcTpe], Type[destTpe], createPlan[srcTpe, destTpe])

      case (source: Product, dest: Product) =>
        val fieldPlans = dest.fields.map { (destField, destFieldStruct) =>
          val sourceFieldStruct = source.fields(destField)
          destField -> recurse(sourceFieldStruct, destFieldStruct)
        }
        Plan.BetweenProducts(source.tpe, dest.tpe, fieldPlans)

      case (source: Coproduct, dest: Coproduct) =>
        val casePlans = source.children.map { (sourceName, sourceCaseStruct) =>
          val destCaseStruct = dest.children(sourceName)
          sourceName -> recurse(sourceCaseStruct, destCaseStruct)
        }
        Plan.BetweenCoproducts(source.tpe, dest.tpe, casePlans)

      case (source: Structure.Singleton, dest: Structure.Singleton) if source.name == dest.name =>
        Plan.BetweenSingletons(source.tpe, dest.tpe, dest.value)

      case other => quotes.reflect.report.errorAndAbort(s"Couldn't construct a plan with structures: $other")
    }

  object UserDefinedTransformation {
    def unapply(structs: (Structure, Structure))(using Quotes): Option[Expr[UserDefinedTransformer[?, ?]]] = {
      val (src, dest) = structs

      (src.tpe -> dest.tpe) match {
        case '[src] -> '[dest] => Expr.summon[UserDefinedTransformer[src, dest]]
      }
    }
  }
}
