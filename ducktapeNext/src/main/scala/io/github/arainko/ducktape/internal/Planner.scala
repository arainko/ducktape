package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.Transformer
import io.github.arainko.ducktape.internal.*

import scala.quoted.*

object Planner {
  import Structure.*

  def between(source: Structure, dest: Structure)(using Quotes) = {
    given Depth = Depth.zero
    recurse(source, dest, Path.empty(source.tpe), Path.empty(dest.tpe))
  }

  private def recurse(
    source: Structure,
    dest: Structure,
    sourceContext: Path,
    destContext: Path
  )(using quotes: Quotes, depth: Depth): Plan[Plan.Error] = {
    import quotes.reflect.*
    given Depth = Depth.incremented(using depth)

    Logger.loggedInfo("Plan"):
      (source.force -> dest.force) match {
        case _ if Depth.current > 64 =>
          Plan.Error(source.tpe, dest.tpe, sourceContext, destContext, ErrorMessage.RecursionSuspected, None)

        case (source: Product, dest: Function) =>
          planProductFunctionTransformation(source, dest, sourceContext, destContext)

        case UserDefinedTransformation(transformer) =>
          Plan.UserDefined(source.tpe, dest.tpe, sourceContext, destContext, transformer)

        case (source, dest) if source.tpe.repr <:< dest.tpe.repr =>
          Plan.Upcast(source.tpe, dest.tpe, sourceContext, destContext)

        case Optional(_, srcName, srcParamStruct) -> Optional(_, destName, destParamStruct) =>
          Plan.BetweenOptions(
            srcParamStruct.tpe,
            destParamStruct.tpe,
            sourceContext,
            destContext,
            recurse(srcParamStruct, destParamStruct, sourceContext, destContext)
          )

        case struct -> Optional(_, _, paramStruct) =>
          Plan.BetweenNonOptionOption(
            struct.tpe,
            paramStruct.tpe,
            sourceContext,
            destContext,
            recurse(struct, paramStruct, sourceContext, destContext)
          )

        case Collection(_, _, srcParamStruct) -> Collection(destCollTpe, _, destParamStruct) =>
          Plan.BetweenCollections(
            destCollTpe,
            srcParamStruct.tpe,
            destParamStruct.tpe,
            sourceContext,
            destContext,
            recurse(srcParamStruct, destParamStruct, sourceContext, destContext)
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
            ErrorMessage.CouldntBuildTransformation(source.tpe, dest.tpe),
            None
          )
      }
  }

  private def planProductTransformation(
    source: Structure.Product,
    dest: Structure.Product,
    sourceContext: Path,
    destContext: Path
  )(using Quotes, Depth) = {
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
              ErrorMessage.NoFieldFound(destField, source.tpe),
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
  )(using Quotes, Depth) = {
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
              ErrorMessage.NoFieldFound(destField, source.tpe),
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
  )(using Quotes, Depth) = {
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
            ErrorMessage.NoChildFound(sourceName, dest.tpe),
            None
          )
        )
    }
    Plan.BetweenCoproducts(source.tpe, dest.tpe, sourceContext, destContext, casePlans.toVector)
  }

  object UserDefinedTransformation {
    def unapply(structs: (Structure, Structure))(using Quotes, Depth): Option[Expr[Transformer[?, ?]]] = {
      val (src, dest) = structs

      // if current depth is lower or equal to 1 then that means we're most likely referring to ourselves
      if Depth.current <= 1 then None
      else
        (src.tpe -> dest.tpe) match {
          case '[src] -> '[dest] => Expr.summon[Transformer[src, dest]]
        }
    }
  }

  object DerivedTransformation {
    def unapply(structs: (Structure, Structure))(using Quotes): Option[Expr[Transformer.Derived[?, ?]]] = {
      val (src, dest) = structs

      (src.tpe -> dest.tpe) match {
        case '[src] -> '[dest] => Expr.summon[Transformer.Derived[src, dest]]
      }
    }
  }
}
