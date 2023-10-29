package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.Transformer
import io.github.arainko.ducktape.internal.*

import scala.quoted.*

object Planner {
  import Structure.*

  def betweenTypeAndFunction[Source: Type](
    function: io.github.arainko.ducktape.internal.Function
  )(using Quotes): Plan[Plan.Error] = {

    val sourceStruct = Structure.of[Source]
    val destStruct = Structure.fromFunction(function)
    recurse(sourceStruct, destStruct, Path.empty(Type.of[Source]), Path.empty(destStruct.tpe))
  }

  def betweenTypes[Source: Type, Dest: Type](using Quotes): Plan[Plan.Error] =
    recurseAndCreatePlan[Source, Dest](Path.empty(Type.of[Source]), Path.empty(Type.of[Dest]))

  private def recurseAndCreatePlan[Source: Type, Dest: Type](
    sourceContext: Path,
    destContext: Path
  )(using Quotes): Plan[Plan.Error] = {
    val src = Structure.of[Source]
    val dest = Structure.of[Dest]
    recurse(src, dest, sourceContext, destContext)
  }

  private def recurse(
    source: Structure,
    dest: Structure,
    sourceContext: Path,
    destContext: Path
  )(using Quotes): Plan[Plan.Error] = {
    import quotes.reflect.*

    Logger.loggedInfo("Plan"):
      (source.force -> dest.force) match {
        case (source: Product, dest: Function) =>
          planProductFunctionTransformation(source, dest, sourceContext, destContext)

        case UserDefinedTransformation(transformer) =>
          Plan.UserDefined(source.tpe, dest.tpe, sourceContext, destContext, transformer)

        case (source, dest) if source.tpe.repr <:< dest.tpe.repr =>
          Plan.Upcast(source.tpe, dest.tpe, sourceContext, destContext)

        case Structure('[Option[srcTpe]], srcName) -> Structure('[Option[destTpe]], destName) =>
          Plan.BetweenOptions(
            Type.of[srcTpe],
            Type.of[destTpe],
            sourceContext,
            destContext,
            recurseAndCreatePlan[srcTpe, destTpe](sourceContext, destContext)
          )

        case Structure('[a], _) -> Structure('[Option[destTpe]], destName) =>
          Plan.BetweenNonOptionOption(
            Type.of[a],
            Type.of[destTpe],
            sourceContext,
            destContext,
            recurseAndCreatePlan[a, destTpe](sourceContext, destContext)
          )

        case Structure(source @ '[Iterable[srcTpe]], srcName) -> Structure(dest @ '[Iterable[destTpe]], destName) =>
          Plan.BetweenCollections(
            dest,
            Type.of[srcTpe],
            Type.of[destTpe],
            sourceContext,
            destContext,
            recurseAndCreatePlan[srcTpe, destTpe](sourceContext, destContext)
          )

        case (source: Product, dest: Product) =>
          planProductTransformation(source, dest, sourceContext, destContext)

        case (source: Coproduct, dest: Coproduct) =>
          planCoproductTransformation(source, dest, sourceContext, destContext)

        case (source: Structure.Singleton, dest: Structure.Singleton) if source.name == dest.name =>
          Plan.BetweenSingletons(source.tpe, dest.tpe, sourceContext, destContext, dest.value)

        case (source: ValueClass, dest) if source.paramTpe.repr <:< dest.tpe.repr =>
          Plan.BetweenWrappedUnwrapped(source.tpe, dest.tpe, sourceContext, destContext, source.paramFieldName)

        case (source, dest: ValueClass) if source.tpe.repr <:< dest.paramTpe.repr =>
          Plan.BetweenUnwrappedWrapped(source.tpe, dest.tpe, sourceContext, destContext)

        case DerivedTransformation(transformer) =>
          Plan.Derived(source.tpe, dest.tpe, sourceContext, destContext, transformer)

        case (source, dest) =>
          Plan.Error(
            source.tpe,
            dest.tpe,
            sourceContext,
            destContext,
            s"Couldn't build a transformation plan between ${source.tpe.repr.show} and ${dest.tpe.repr.show}",
            None
          )
      }
  }

  private def planProductTransformation(
    source: Structure.Product,
    dest: Structure.Product,
    sourceContext: Path,
    destContext: Path
  )(using Quotes) = {
    val fieldPlans = dest.fields.map { (destField, destFieldStruct) =>
      val updatedDestContext = destContext.appended(Path.Segment.Field(destFieldStruct.tpe, destField))
      val plan =
        source.fields
          .get(destField)
          .map { sourceStruct =>
            val updatedSourceContext = sourceContext.appended(Path.Segment.Field(sourceStruct.tpe, destField))
            recurse(sourceStruct, destFieldStruct, updatedSourceContext, updatedDestContext)
          }
          .getOrElse(
            Plan.Error(
              Type.of[Nothing],
              destFieldStruct.tpe,
              sourceContext, // TODO: Revise
              updatedDestContext, // TODO: Revise
              s"No field named '$destField' found in ${source.tpe.repr.show}",
              None
            )
          )
      destField -> plan
    }
    Plan.BetweenProducts(source.tpe, dest.tpe, sourceContext, destContext, fieldPlans)
  }

  private def planProductFunctionTransformation(
    source: Structure.Product,
    dest: Structure.Function,
    sourceContext: Path,
    destContext: Path
  )(using Quotes) = {
    val argPlans = dest.args.map { (destField, destFieldStruct) =>
      val updatedDestContext = destContext.appended(Path.Segment.Field(destFieldStruct.tpe, destField))
      val plan =
        source.fields
          .get(destField)
          .map { sourceStruct =>
            val updatedSourceContext = sourceContext.appended(Path.Segment.Field(sourceStruct.tpe, destField))
            recurse(sourceStruct, destFieldStruct, updatedSourceContext, updatedDestContext)
          }
          .getOrElse(
            Plan.Error(
              Type.of[Nothing],
              destFieldStruct.tpe,
              sourceContext, // TODO: Revise
              updatedDestContext, // TODO: Revise
              s"No field named '$destField' found in ${source.tpe.repr.show}",
              None
            )
          )
      destField -> plan
    }
    Plan.BetweenProductFunction(source.tpe, dest.tpe, sourceContext, destContext, argPlans, dest.function)
  }

  private def planCoproductTransformation(
    source: Structure.Coproduct,
    dest: Structure.Coproduct,
    sourceContext: Path,
    destContext: Path
  )(using Quotes) = {
    val casePlans = source.children.map { (sourceName, sourceCaseStruct) =>
      val updatedSourceContext = sourceContext.appended(Path.Segment.Case(sourceCaseStruct.tpe))

      dest.children
        .get(sourceName)
        .map { destCaseStruct =>
          val updatedDestContext = destContext.appended(Path.Segment.Case(destCaseStruct.tpe))
          recurse(sourceCaseStruct, destCaseStruct, updatedSourceContext, updatedDestContext)
        }
        .getOrElse(
          Plan.Error(
            sourceCaseStruct.tpe,
            Type.of[Any],
            updatedSourceContext, // TODO: Revise
            destContext, // TODO: Revise
            s"No child named '$sourceName' found in ${dest.tpe.repr.show}",
            None
          )
        )
    }
    Plan.BetweenCoproducts(source.tpe, dest.tpe, sourceContext, destContext, casePlans.toVector)
  }

  object UserDefinedTransformation {
    def unapply(structs: (Structure, Structure))(using Quotes): Option[Expr[Transformer[?, ?]]] = {
      val (src, dest) = structs

      (src.tpe -> dest.tpe) match {
        case '[src] -> '[dest] => Expr.summon[Transformer[src, dest]]
      }
    }
  }

  object DerivedTransformation {
    def unapply(structs: (Structure, Structure))(using Quotes): Option[Expr[Transformer[?, ?]]] = {
      val (src, dest) = structs

      (src.tpe -> dest.tpe) match {
        case '[src] -> '[dest] => Expr.summon[Transformer[src, dest]]
      }
    }
  }
}
