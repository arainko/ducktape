package io.github.arainko.ducktape

import scala.quoted.*
import io.github.arainko.ducktape.internal.modules.*
import io.github.arainko.ducktape.Plan.Context

object Planner {
  import Structure.*

  def createPlan[Source: Type, Dest: Type](using Quotes): Plan[Plan.Error] =
    recurseAndCreatePlan[Source, Dest](Plan.Context.empty(Type[Source], Type[Dest]))

  private def recurseAndCreatePlan[Source: Type, Dest: Type](context: Plan.Context)(using Quotes): Plan[Plan.Error] = {
    val src = Structure.of[Source]
    val dest = Structure.of[Dest]
    val plan = recurse(src, dest, context)
    println()
    println(plan.show)
    plan
    // plan
  }

  private def recurse(source: Structure, dest: Structure, context: Plan.Context)(using Quotes): Plan[Plan.Error] =
    (source.force -> dest.force) match {
      case UserDefinedTransformation(transformer) =>
        Plan.UserDefined(source.tpe, dest.tpe, transformer)

      case (source, dest) if source.tpe.repr <:< dest.tpe.repr =>
        Plan.Upcast(source.tpe, dest.tpe)

      case Structure('[Option[srcTpe]], srcName) -> Structure('[Option[destTpe]], destName) =>
        Plan.BetweenOptions(Type[srcTpe], Type[destTpe], recurseAndCreatePlan[srcTpe, destTpe](context))

      case Structure('[a], _) -> Structure('[Option[destTpe]], destName) =>
        Plan.BetweenNonOptionOption(Type[a], Type[destTpe], recurseAndCreatePlan[a, destTpe](context))

      case Structure(source @ '[Iterable[srcTpe]], srcName) -> Structure(dest @ '[Iterable[destTpe]], destName) =>
        Plan.BetweenCollections(dest, Type[srcTpe], Type[destTpe], recurseAndCreatePlan[srcTpe, destTpe](context))

      case (source: Product, dest: Product) =>
        val fieldPlans = dest.fields.map { (destField, destFieldStruct) =>
          val updatedContext = context.add(Path.Segment.Field(destFieldStruct.tpe, destField))
          val plan =
            source.fields
              .get(destField)
              .map(recurse(_, destFieldStruct, updatedContext))
              .getOrElse(
                Plan.Error(
                  Type.of[Nothing],
                  destFieldStruct.tpe,
                  updatedContext,
                  s"No field named '$destField' found in ${source.tpe.repr.show}"
                )
              )
          destField -> plan
        }
        Plan.BetweenProducts(source.tpe, dest.tpe, fieldPlans)

      case (source: Coproduct, dest: Coproduct) =>
        val casePlans = source.children.map { (sourceName, sourceCaseStruct) =>
          val updatedContext = context.add(Path.Segment.Case(sourceCaseStruct.tpe))

          dest.children
            .get(sourceName)
            .map(recurse(sourceCaseStruct, _, updatedContext))
            .getOrElse(
              Plan.Error(
                sourceCaseStruct.tpe,
                Type.of[Any],
                updatedContext,
                s"No child named '$sourceName' found in ${dest.tpe.repr.show}"
              )
            )
        }
        Plan.BetweenCoproducts(source.tpe, dest.tpe, casePlans.toVector)

      case (source: Structure.Singleton, dest: Structure.Singleton) if source.name == dest.name =>
        Plan.BetweenSingletons(source.tpe, dest.tpe, dest.value)

      case (source: ValueClass, dest) if source.paramTpe.repr <:< dest.tpe.repr =>
        Plan.BetweenWrappedUnwrapped(source.tpe, dest.tpe, source.paramFieldName)

      case (source, dest: ValueClass) if source.tpe.repr <:< dest.paramTpe.repr =>
        Plan.BetweenUnwrappedWrapped(source.tpe, dest.tpe)

      case DerivedTransformation(transformer) =>
        Plan.Derived(source.tpe, dest.tpe, transformer)

      case (source, dest) =>
        Plan.Error(
          source.tpe,
          dest.tpe,
          context,
          s"Couldn't build a transformation plan between ${source.tpe.repr.show} and ${dest.tpe.repr.show}"
        )
    }

  object UserDefinedTransformation {
    def unapply(structs: (Structure, Structure))(using Quotes): Option[Expr[UserDefinedTransformer[?, ?]]] = {
      val (src, dest) = structs

      (src.tpe -> dest.tpe) match {
        case '[src] -> '[dest] => Expr.summon[UserDefinedTransformer[src, dest]]
      }
    }
  }

  object DerivedTransformation {
    def unapply(structs: (Structure, Structure))(using Quotes): Option[Expr[Transformer2[?, ?]]] = {
      val (src, dest) = structs

      (src.tpe -> dest.tpe) match {
        case '[src] -> '[dest] => Expr.summon[Transformer2[src, dest]]
      }
    }
  }

  inline def print[A, B] = ${ printMacro[A, B] }

  def printMacro[A: Type, B: Type](using Quotes): Expr[Unit] = {
    val plan = createPlan[A, B]
    quotes.reflect.report.info(plan.show)
    '{}
  }
}
