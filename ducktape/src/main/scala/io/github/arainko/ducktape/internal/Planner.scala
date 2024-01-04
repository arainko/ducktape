package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.Transformer
import io.github.arainko.ducktape.internal.*

import scala.quoted.*

private[ducktape] object Planner {
  import Structure.*

  def between(source: Structure, dest: Structure)(using Quotes, TransformationSite) = {
    given Depth = Depth.zero
    recurse(source, dest)
  }

  private def recurse(
    source: Structure,
    dest: Structure
  )(using quotes: Quotes, depth: Depth, transformationSite: TransformationSite): Plan[Plan.Error] = {
    import quotes.reflect.*
    given Depth = Depth.incremented(using depth)

    Logger.loggedDebug(s"Plan @ depth ${Depth.current}"):
      (source.force -> dest.force) match {
        case _ if Depth.current > 64 =>
          Plan.Error(source, dest, ErrorMessage.RecursionSuspected, None)

        case (source: Product, dest: Function) =>
          planProductFunctionTransformation(source, dest)

        case UserDefinedTransformation(transformer) =>
          Plan.UserDefined(source, dest, transformer)

        case (source, dest) if source.tpe.repr <:< dest.tpe.repr =>
          Plan.Upcast(source, dest)

        case (source @ Optional(_, _, srcParamStruct)) -> (dest @ Optional(_, _, destParamStruct)) =>
          Plan.BetweenOptions(
            source,
            dest,
            recurse(srcParamStruct, destParamStruct)
          )

        case source -> (dest @ Optional(_, _, paramStruct)) =>
          Plan.BetweenNonOptionOption(
            source,
            dest,
            recurse(source, paramStruct)
          )

        case (source @ Collection(_, _, srcParamStruct)) -> (dest @ Collection(_, _, destParamStruct)) =>
          Plan.BetweenCollections(
            source,
            dest,
            recurse(srcParamStruct, destParamStruct)
          )

        case (source: Product, dest: Product) =>
          planProductTransformation(source, dest)

        case (source: Coproduct, dest: Coproduct) =>
          planCoproductTransformation(source, dest)

        case (source: Structure.Singleton, dest: Structure.Singleton) if source.name == dest.name =>
          Plan.BetweenSingletons(source, dest)

        case (source: ValueClass, dest) if source.paramTpe.repr <:< dest.tpe.repr =>
          Plan.BetweenWrappedUnwrapped(source, dest, source.paramFieldName)

        case (source, dest: ValueClass) if source.tpe.repr <:< dest.paramTpe.repr =>
          Plan.BetweenUnwrappedWrapped(source, dest)

        case DerivedTransformation(transformer) =>
          Plan.Derived(source, dest, transformer)

        case (source, dest) =>
          Plan.Error(
            source,
            dest,
            ErrorMessage.CouldntBuildTransformation(source.tpe, dest.tpe),
            None
          )
      }
  }

  private def planProductTransformation(
    source: Structure.Product,
    dest: Structure.Product
  )(using Quotes, Depth, TransformationSite) = {
    val fieldPlans = dest.fields.map { (destField, destFieldStruct) =>
      val plan =
        source.fields
          .get(destField)
          .map { sourceStruct =>
            recurse(sourceStruct, destFieldStruct)
          }
          .getOrElse(
            Plan.Error(
              Structure.of[Nothing](source.path),
              destFieldStruct,
              ErrorMessage.NoFieldFound(destField, destFieldStruct.tpe, source.tpe),
              None
            )
          )
      destField -> plan
    }
    Plan.BetweenProducts(source, dest, fieldPlans)
  }

  private def planProductFunctionTransformation(
    source: Structure.Product,
    dest: Structure.Function
  )(using Quotes, Depth, TransformationSite) = {
    val argPlans = dest.args.map { (destField, destFieldStruct) =>
      val plan =
        source.fields
          .get(destField)
          .map { sourceStruct =>
            recurse(sourceStruct, destFieldStruct)
          }
          .getOrElse(
            Plan.Error(
              Structure.of[Nothing](source.path),
              destFieldStruct,
              ErrorMessage.NoFieldFound(destField, destFieldStruct.tpe, source.tpe),
              None
            )
          )
      destField -> plan
    }
    Plan.BetweenProductFunction(source, dest, argPlans)
  }

  private def planCoproductTransformation(
    source: Structure.Coproduct,
    dest: Structure.Coproduct
  )(using Quotes, Depth, TransformationSite) = {
    val casePlans = source.children.map { (sourceName, sourceCaseStruct) =>

      dest.children
        .get(sourceName)
        .map { destCaseStruct =>
          recurse(sourceCaseStruct, destCaseStruct)
        }
        .getOrElse(
          Plan.Error(
            sourceCaseStruct,
            Structure.of[Any](dest.path),
            ErrorMessage.NoChildFound(sourceName, dest.tpe),
            None
          )
        )
    }
    Plan.BetweenCoproducts(source, dest, casePlans.toVector)
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
