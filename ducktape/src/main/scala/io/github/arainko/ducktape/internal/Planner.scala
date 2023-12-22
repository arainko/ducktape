package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.Transformer
import io.github.arainko.ducktape.internal.*

import scala.quoted.*

private[ducktape] object Planner {
  import Structure.*

  def between(source: Structure, dest: Structure)(using Quotes, TransformationSite) = {
    given Depth = Depth.zero
    recurse(source, dest, Path.empty(source.tpe), Path.empty(dest.tpe))
  }

  private def recurse(
    source: Structure,
    dest: Structure,
    sourceContext: Path,
    destContext: Path
  )(using quotes: Quotes, depth: Depth, transformationSite: TransformationSite): Plan[Plan.Error] = {
    import quotes.reflect.*
    given Depth = Depth.incremented(using depth)

    Logger.loggedDebug(s"Plan @ depth ${Depth.current}"):
      (source.force -> dest.force) match {
        case _ if Depth.current > 64 =>
          Plan.Error(source, dest, sourceContext, destContext, ErrorMessage.RecursionSuspected, None)

        case (source: Product, dest: Function) =>
          planProductFunctionTransformation(source, dest, sourceContext, destContext)

        case UserDefinedTransformation(transformer) =>
          Plan.UserDefined(source, dest, sourceContext, destContext, transformer)

        case (source, dest) if source.tpe.repr <:< dest.tpe.repr =>
          Plan.Upcast(source, dest, sourceContext, destContext)

        case Optional(_, _, srcParamStruct) -> Optional(_, _, destParamStruct) =>
          val updatedSourceContext = sourceContext.appended(Path.Segment.Element(srcParamStruct.tpe))
          val updatedDestContext = destContext.appended(Path.Segment.Element(destParamStruct.tpe))

          Plan.BetweenOptions(
            srcParamStruct,
            destParamStruct,
            sourceContext,
            destContext,
            recurse(srcParamStruct, destParamStruct, updatedSourceContext, updatedDestContext)
          )

        case struct -> Optional(_, _, paramStruct) =>
          Plan.BetweenNonOptionOption(
            struct,
            paramStruct,
            sourceContext,
            destContext,
            recurse(struct, paramStruct, sourceContext, destContext.appended(Path.Segment.Element(paramStruct.tpe)))
          )

        case Collection(_, _, srcParamStruct) -> Collection(destCollTpe, _, destParamStruct) =>
          val updatedSourceContext = sourceContext.appended(Path.Segment.Element(srcParamStruct.tpe))
          val updatedDestContext = destContext.appended(Path.Segment.Element(destParamStruct.tpe))

          Plan.BetweenCollections(
            destCollTpe,
            srcParamStruct,
            destParamStruct,
            sourceContext,
            destContext,
            recurse(srcParamStruct, destParamStruct, updatedSourceContext, updatedDestContext)
          )

        case (source: Product, dest: Product) =>
          planProductTransformation(source, dest, sourceContext, destContext)

        case (source: Coproduct, dest: Coproduct) =>
          planCoproductTransformation(source, dest, sourceContext, destContext)

        case (source: Structure.Singleton, dest: Structure.Singleton) if source.name == dest.name =>
          Plan.BetweenSingletons(source, dest, sourceContext, destContext, dest.value)

        case (source: ValueClass, dest) if source.paramTpe.repr <:< dest.tpe.repr =>
          Plan.BetweenWrappedUnwrapped(source, dest, sourceContext, destContext, source.paramFieldName)

        case (source, dest: ValueClass) if source.tpe.repr <:< dest.paramTpe.repr =>
          Plan.BetweenUnwrappedWrapped(source, dest, sourceContext, destContext)

        case DerivedTransformation(transformer) =>
          Plan.Derived(source, dest, sourceContext, destContext, transformer)

        case (source, dest) =>
          Plan.Error(
            source,
            dest,
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
  )(using Quotes, Depth, TransformationSite) = {
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
              Structure.of[Nothing](None),
              destFieldStruct,
              sourceContext, // TODO: Revise
              updatedDestContext, // TODO: Revise
              ErrorMessage.NoFieldFound(destField, destFieldStruct.tpe, source.tpe),
              None
            )
          )
      destField -> plan
    }
    Plan.BetweenProducts(source, dest, sourceContext, destContext, fieldPlans)
  }

  private def planProductFunctionTransformation(
    source: Structure.Product,
    dest: Structure.Function,
    sourceContext: Path,
    destContext: Path
  )(using Quotes, Depth, TransformationSite) = {
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
              Structure.of[Nothing](None),
              destFieldStruct,
              sourceContext, // TODO: Revise
              updatedDestContext, // TODO: Revise
              ErrorMessage.NoFieldFound(destField, destFieldStruct.tpe, source.tpe),
              None
            )
          )
      destField -> plan
    }
    Plan.BetweenProductFunction(source, dest, sourceContext, destContext, argPlans, dest.function)
  }

  private def planCoproductTransformation(
    source: Structure.Coproduct,
    dest: Structure.Coproduct,
    sourceContext: Path,
    destContext: Path
  )(using Quotes, Depth, TransformationSite) = {
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
            sourceCaseStruct,
            Structure.of[Any](None),
            updatedSourceContext, // TODO: Revise
            destContext, // TODO: Revise
            ErrorMessage.NoChildFound(sourceName, dest.tpe),
            None
          )
        )
    }
    Plan.BetweenCoproducts(source, dest, sourceContext, destContext, casePlans.toVector)
  }

  object UserDefinedTransformation {
    def unapply(structs: (Structure, Structure))(using Quotes, Depth, TransformationSite): Option[Expr[Transformer[?, ?]]] = {
      val (src, dest) = structs

      def summonTransformer =
        (src.tpe -> dest.tpe) match {
          case '[src] -> '[dest] => Expr.summon[Transformer[src, dest]]
        }

      // if current depth is lower or equal to 1 then that means we're most likely referring to ourselves
      summon[TransformationSite] match {
        case TransformationSite.Definition if Depth.current <= 1 => None
        case TransformationSite.Definition                       => summonTransformer
        case TransformationSite.Transformation                   => summonTransformer
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
