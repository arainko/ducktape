package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.internal.Context.{ PossiblyFallible, Total }
import io.github.arainko.ducktape.internal.Plan.{ Derived, UserDefined }
import io.github.arainko.ducktape.internal.Summoner.UserDefined.{ FallibleTransformer, TotalTransformer }
import io.github.arainko.ducktape.internal.*

import scala.collection.immutable.VectorMap
import scala.quoted.*
import scala.util.boundary

private[ducktape] object Planner {
  import Structure.*

  def between[F <: Fallible](source: Structure, dest: Structure)(using Quotes, Context.Of[F]) = {
    given Depth = Depth.zero
    recurse(source, dest)
  }

  private def recurse[F <: Fallible](
    source: Structure,
    dest: Structure
  )(using quotes: Quotes, depth: Depth, context: Context.Of[F]): Plan[Erroneous, F] = {
    import quotes.reflect.*
    given Depth = Depth.incremented(using depth)

    Logger.loggedDebug(s"Plan @ depth ${Depth.current}"):
      (source.force -> dest.force) match {
        case _ if Depth.current > 64 =>
          Plan.Error(source, dest, ErrorMessage.RecursionSuspected, None)

        case (source: Product, dest: Function) =>
          planProductFunctionTransformation(source, dest)

        case (source: Tuple, dest: Function) =>
          val plans = positionWisePlans(source, source.elements, dest.args.values.toIndexedSeq)
          val argPlans = dest.args.keys.zip(plans).to(VectorMap)
          Plan.BetweenTupleFunction(source, dest, argPlans)

        case UserDefinedTransformation(transformer) =>
          verifyNotSelfReferential(Plan.UserDefined(source, dest, transformer))

        case (source, dest) if source.tpe.repr <:< dest.tpe.repr =>
          Plan.Upcast(source, dest)

        case BetweenFallibles(plan) => plan

        case BetweenFallibleNonFallible(plan) => plan

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

        // Wrapped(WrapperType.Optional) is isomorphic to Optional
        // scalafmt: { maxColumn = 150 }
        case (source @ Wrapped(_, WrapperType.Optional, _, srcUnderlying)) -> (dest @ Wrapped(_, WrapperType.Optional, _, destUnderlying)) =>
          Plan.BetweenOptions(
            Structure.Optional.fromWrapped(source),
            Structure.Optional.fromWrapped(dest),
            recurse(srcUnderlying, destUnderlying)
          )

        case source -> (dest @ Wrapped(_, WrapperType.Optional, _, underlying)) =>
          Plan.BetweenNonOptionOption(
            source,
            Structure.Optional.fromWrapped(dest),
            recurse(source, underlying)
          )

        case (source @ Collection(_, _, srcParamStruct)) -> (dest @ Collection(_, _, destParamStruct)) =>
          Plan.BetweenCollections(
            source,
            dest,
            recurse(srcParamStruct, destParamStruct)
          )

        case (source: Product, dest: Product) =>
          planProductTransformation(source, dest)

        case (source: Product, dest: Tuple) =>
          val plans = positionWisePlans(source, source.fields.values.toIndexedSeq, dest.elements)
          Plan.BetweenProductTuple(source, dest, plans)

        case (source: Tuple, dest: Product) =>
          val plans = positionWisePlans(source, source.elements, dest.fields.values.toIndexedSeq)
          // safe under the assumption that 'positionWisePlans' always returns dest.fields.size amount of plans
          val fieldPlans = dest.fields.keys.zip(plans).to(VectorMap)
          Plan.BetweenTupleProduct(source, dest, fieldPlans)

        case (source: Structure.Tuple, dest: Structure.Tuple) =>
          val plans = positionWisePlans(source, source.elements, dest.elements)
          Plan.BetweenTuples(source, dest, plans)

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
  )(using Quotes, Depth, Context.Of[F]) = {

    val fieldPlans = dest.fields.map { (destField, destFieldStruct) =>
      val plan =
        source.fields
          .get(destField)
          .map(sourceStruct => recurse(sourceStruct, destFieldStruct))
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

  private def positionWisePlans[F <: Fallible](
    sourceStruct: Structure,
    source: IndexedSeq[Structure],
    dest: IndexedSeq[Structure]
  )(using Quotes, Depth, Context.Of[F]): Vector[Plan[Erroneous, F]] = {
    dest.zipWithIndex.map { (destFieldStruct, index) =>
      source
        .lift(index)
        .map(sourceStruct => recurse(sourceStruct, destFieldStruct))
        .getOrElse(
          Plan.Error(
            Structure.of[Nothing](sourceStruct.path),
            destFieldStruct,
            ErrorMessage.NoFieldFoundAtIndex(index, sourceStruct.tpe),
            None
          )
        )
    }.toVector
  }

  private def planProductFunctionTransformation[F <: Fallible](
    source: Structure.Product,
    dest: Structure.Function
  )(using Quotes, Depth, Context.Of[F]) = {
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
  )(using Quotes, Depth, Context.Of[F]) = {
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
    def unapply[F <: Fallible](structs: (Structure, Structure))(using Quotes, Depth, Context.Of[F]) = {
      val (src, dest) = structs

      def summonTransformer(using Quotes) =
        (src.tpe -> dest.tpe) match {
          case '[src] -> '[dest] => Context.current.summoner.summonUserDefined[src, dest]
        }

      // if current depth is lower or equal to 1 then that means we're most likely referring to ourselves
      Context.current.transformationSite match {
        case TransformationSite.Definition if Depth.current <= 1 => None
        case TransformationSite.Definition                       => summonTransformer
        case TransformationSite.Transformation                   => summonTransformer
      }
    }
  }

  object DerivedTransformation {
    def unapply[F <: Fallible](structs: (Structure, Structure))(using Quotes, Context.Of[F]) = {
      val (src, dest) = structs

      (src.tpe -> dest.tpe) match {
        case '[src] -> '[dest] => Context.current.summoner.summonDerived[src, dest]
      }
    }
  }

  private def verifyNotSelfReferential(
    plan: Plan.Derived[Fallible] | Plan.UserDefined[Fallible]
  )(using Context, Depth, Quotes): Plan.Error | plan.type = {
    import quotes.reflect.*

    val transformerExpr = plan match
      case UserDefined(source, dest, Summoner.UserDefined.TotalTransformer(t))    => t
      case UserDefined(source, dest, Summoner.UserDefined.FallibleTransformer(t)) => t
      case Derived(source, dest, Summoner.Derived.TotalTransformer(t))            => t
      case Derived(source, dest, Summoner.Derived.FallibleTransformer(t))         => t

    val transformerSymbol = transformerExpr.asTerm.symbol

    Context.current.transformationSite match
      case TransformationSite.Transformation if Depth.current == 1 =>
        boundary[Plan.Error | plan.type]:
          var owner = Symbol.spliceOwner
          while (!owner.isNoSymbol) {
            if owner == transformerSymbol then boundary.break(Plan.Error.from(plan, ErrorMessage.LoopingTransformerDetected, None))
            owner = owner.maybeOwner
          }
          plan
      case _ => plan

  }

  object BetweenFallibleNonFallible {
    def unapply[F <: Fallible](
      structs: (Structure, Structure)
    )(using Quotes, Depth, Context.Of[F]): Option[Plan[Erroneous, F]] =
      PartialFunction.condOpt(Context.current *: structs) {
        case (ctx: Context.PossiblyFallible[f], source @ Wrapped(tpe, _, path, underlying), dest) =>
          // needed for the recurse call to return Plan[Erroneous, Nothing]
          given Context.Total = ctx.toTotal
          val plan = Plan.BetweenFallibleNonFallible(
            source,
            dest,
            recurse(underlying, dest)
          )

          // the compiler needs a bit more encouragement to be sure that the plan we construct has a fallibility of F
          // Context.PossiblyFallible is defined with a type F = Fallible so we can deduce that ctx.F =:= Fallible =:= F
          ctx.reifyPlan[F](plan)
      }

  }

  object BetweenFallibles {
    def unapply[F <: Fallible](
      structs: (Structure, Structure)
    )(using Quotes, Depth, Context.Of[F]): Option[Plan[Erroneous, F]] =
      PartialFunction.condOpt(Context.current *: structs) {
        case (
              ctx @ Context.PossiblyFallible(_, _, _, mode: TransformationMode.FailFast[f]),
              source @ Wrapped(tpe, _, path, underlying),
              dest
            ) =>
          ctx.reifyPlan[F] {
            Plan.BetweenFallibles(
              source,
              dest,
              mode,
              recurse(underlying, dest)
            )
          }

        case (
              ctx @ Context.PossiblyFallible(_, _, _, TransformationMode.Accumulating(mode, Some(localMode))),
              source @ Wrapped(tpe, _, path, underlying),
              dest
            ) =>
          ctx.reifyPlan[F] {
            Plan.BetweenFallibles(
              source,
              dest,
              TransformationMode.FailFast(localMode),
              recurse(underlying, dest)
            )
          }
      }
  }
}
