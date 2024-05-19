package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.internal.Plan.{ Derived, UserDefined }
import io.github.arainko.ducktape.internal.Summoner.UserDefined.{ FallibleTransformer, TotalTransformer }
import io.github.arainko.ducktape.internal.*

import scala.quoted.*
import scala.util.boundary

private[ducktape] object Planner {
  import Structure.*

  def between[F <: Fallible](source: Structure, dest: Structure)(using Quotes, TransformationSite, Summoner[F]) = {
    given Depth = Depth.zero
    recurse(source, dest)
  }

  private def recurse[F <: Fallible](
    source: Structure,
    dest: Structure
  )(using quotes: Quotes, depth: Depth, transformationSite: TransformationSite, summoner: Summoner[F]): Plan[Plan.Error, F] = {
    import quotes.reflect.*
    given Depth = Depth.incremented(using depth)

    Logger.loggedDebug(s"Plan @ depth ${Depth.current}"):
      (source.force -> dest.force) match {
        case _ if Depth.current > 64 =>
          Plan.Error(source, dest, ErrorMessage.RecursionSuspected, None)

        case (source: Product, dest: Function) =>
          planProductFunctionTransformation(source, dest)

        case UserDefinedTransformation(transformer) =>
          verifyNotSelfReferential(Plan.UserDefined(source, dest, transformer))

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
          verifyNotSelfReferential(Plan.Derived(source, dest, transformer))

        case (source, dest) =>
          Plan.Error(
            source,
            dest,
            ErrorMessage.CouldntBuildTransformation(source.tpe, dest.tpe),
            None
          )
      }
  }

  private def planProductTransformation[F <: Fallible](
    source: Structure.Product,
    dest: Structure.Product
  )(using Quotes, Depth, TransformationSite, Summoner[F]) = {
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

  private def planProductFunctionTransformation[F <: Fallible](
    source: Structure.Product,
    dest: Structure.Function
  )(using Quotes, Depth, TransformationSite, Summoner[F]) = {
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

  private def planCoproductTransformation[F <: Fallible](
    source: Structure.Coproduct,
    dest: Structure.Coproduct
  )(using Quotes, Depth, TransformationSite, Summoner[F]) = {
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
    def unapply[F <: Fallible](structs: (Structure, Structure))(using Quotes, Depth, TransformationSite, Summoner[F]) = {
      val (src, dest) = structs

      def summonTransformer(using Quotes) =
        (src.tpe -> dest.tpe) match {
          case '[src] -> '[dest] => Summoner[F].summonUserDefined[src, dest]
        }

      // if current depth is lower or equal to 1 then that means we're most likely referring to ourselves
      TransformationSite.current match {
        case TransformationSite.Definition if Depth.current <= 1 => None
        case TransformationSite.Definition                       => summonTransformer
        case TransformationSite.Transformation                   => summonTransformer
      }
    }
  }

  object DerivedTransformation {
    def unapply[F <: Fallible](structs: (Structure, Structure))(using Quotes, Summoner[F]) = {
      val (src, dest) = structs

      (src.tpe -> dest.tpe) match {
        case '[src] -> '[dest] => Summoner[F].summonDerived[src, dest]
      }
    }
  }

  private def verifyNotSelfReferential(
    plan: Plan.Derived[Fallible] | Plan.UserDefined[Fallible]
  )(using TransformationSite, Depth, Quotes): Plan.Error | plan.type = {
    import quotes.reflect.*

    val transformerExpr = plan match
      case UserDefined(source, dest, Summoner.UserDefined.TotalTransformer(t))    => t
      case UserDefined(source, dest, Summoner.UserDefined.FallibleTransformer(t)) => t
      case Derived(source, dest, Summoner.Derived.TotalTransformer(t))            => t
      case Derived(source, dest, Summoner.Derived.FallibleTransformer(t))         => t

    val transformerSymbol = transformerExpr.asTerm.symbol

    TransformationSite.current match
      case TransformationSite.Transformation if Depth.current == 1 =>
        boundary[Plan.Error | plan.type]:
          var owner = Symbol.spliceOwner
          while (!owner.isNoSymbol) {
            if owner == transformerSymbol then
              boundary.break(Plan.Error.from(plan, ErrorMessage.LoopingTransformerDetected, None))
            owner = owner.maybeOwner
          }
          plan
      case _ => plan

  }
}
