package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.internal.*
import io.github.arainko.ducktape.internal.Context.{ PossiblyFallible, Total }
import io.github.arainko.ducktape.internal.Plan.{ Derived, UserDefined }
import io.github.arainko.ducktape.internal.Summoner.UserDefined.{ FallibleTransformer, TotalTransformer }

import scala.collection.Factory
import scala.collection.immutable.VectorMap
import scala.quoted.*
import scala.util.boundary

private[ducktape] object Planner {
  import Structure.*
  private enum FallthroughUpcast {
    case Yes, No
  }

  def between[F <: Fallible](source: Structure, dest: Structure, flags: PlanFlags)(using Quotes, Context.Of[F]) = {
    given Depth = Depth.zero
    given PlanFlags = flags
    recurse(source, dest)
  }

  private def recurse[F <: Fallible](
    source: Structure,
    dest: Structure,
    // TODO: Come up with something nicer
    noUpcast: FallthroughUpcast = FallthroughUpcast.No
  )(using quotes: Quotes, depth: Depth, context: Context.Of[F], flags: PlanFlags): Plan[Erroneous, F] = {
    import quotes.reflect.*
    given Depth = depth.incremented

    println(flags.toString())

    Logger.loggedDebug(s"Plan @ depth ${Depth.current}"):
      (source.force -> dest.force) match {
        case _ if Depth.current > 64 =>
          Plan.Error(source, dest, ErrorMessage.RecursionSuspected, None)

        case (source: Product, dest: Function) =>
          planProductFunctionTransformation(source, dest)

        case (source: Tuple, dest: Function) =>
          planTupleFunctionTransformation(source, dest)

        case UserDefinedTransformation(transformer) =>
          verifyNotSelfReferential(Plan.UserDefined(source, dest, transformer))

        case (source, dest) if noUpcast == FallthroughUpcast.No && source.tpe.repr <:< dest.tpe.repr =>
          // Don't allow fallible transformations in the alternative case
          Plan.Upcast(source, dest, () => context.toTotal.locally(recurse(source, dest, FallthroughUpcast.Yes)))

        case BetweenFallibles(plan) => plan

        case BetweenFallibleNonFallible(plan) => plan

        case (source @ Optional(_, _, srcParamStruct)) -> (dest @ Optional(_, _, destParamStruct)) =>
          Plan.BetweenOptions(
            source,
            dest,
            PlanFlags.current.transition(Step.Element, Step.Element).locally {
              recurse(srcParamStruct, destParamStruct)
            }
          )

        case source -> (dest @ Optional(_, _, paramStruct)) =>
          Plan.BetweenNonOptionOption(
            source,
            dest,
            PlanFlags.current.transition(Passthrough, Step.Element).locally {
              recurse(source, paramStruct)
            }
          )

        // Wrapped(WrapperType.Optional) is isomorphic to Optional
        // scalafmt: { maxColumn = 150 }
        case (source @ Wrapped(_, WrapperType.Optional, _, srcUnderlying)) -> (dest @ Wrapped(_, WrapperType.Optional, _, destUnderlying)) =>
          Plan.BetweenOptions(
            Structure.Optional.fromWrapped(source),
            Structure.Optional.fromWrapped(dest),
            PlanFlags.current.transition(Step.Element, Step.Element).locally {
              recurse(srcUnderlying, destUnderlying)
            }
          )

        case source -> (dest @ Wrapped(_, WrapperType.Optional, _, underlying)) =>
          Plan.BetweenNonOptionOption(
            source,
            Structure.Optional.fromWrapped(dest),
            PlanFlags.current.transition(Passthrough, Step.Element).locally {
              recurse(source, underlying)
            }
          )

        case (source @ Collection(_, _, srcParamStruct)) -> (dest @ Collection('[destColl], _, destParamStruct @ Structure('[destElem]))) =>
          Implicits.search(TypeRepr.of[Factory[destElem, destColl]]) match {
            case success: ImplicitSearchSuccess =>
              Plan.BetweenCollections(
                source,
                dest,
                success.tree.asExprOf[Factory[destElem, destColl]],
                PlanFlags.current.transition(Step.Element, Step.Element).locally {
                  recurse(srcParamStruct, destParamStruct)
                }
              )
            case failure: ImplicitSearchFailure =>
              Plan.Error(
                source,
                dest,
                ErrorMessage.CollectionFactoryNotFound(dest, failure.explanation),
                None
              )

          }

        case (source: Product, dest: Product) =>
          // TODO: needs a separte effect type for field renames and case renames - not a joint one
          (PlanFlags.current.dest.get[Flag.Effect.Rename], PlanFlags.current.source.get[Flag.Effect.Rename]) match {
            case (Some(destRename), srcRename) =>
              planProductTransformationWithModifiedNames(source, dest, srcRename.fold(identity[String])(_.renamer), destRename.renamer)
            case (destRename, Some(srcRename)) =>
              planProductTransformationWithModifiedNames(source, dest, srcRename.renamer, destRename.fold(identity[String])(_.renamer))
            case _ =>
              planProductTransformation(source, dest)
          }

        case (source: Product, dest: Tuple) =>
          planProductTupleTransformation(source, dest)

        case (source: Tuple, dest: Product) =>
          planTupleProductTransformation(source, dest)

        case (source: Structure.Tuple, dest: Structure.Tuple) =>
          planTupleTransformation(source, dest)

        case (source: Coproduct, dest: Coproduct) =>
          // TODO: needs a separte effect type for field renames and case renames - not a joint one

          (PlanFlags.current.dest.get[Flag.Effect.Rename], PlanFlags.current.source.get[Flag.Effect.Rename]) match {
            case (Some(destRename), srcRename) =>
              planCoproductTransformationWithModifiedNames(source, dest, srcRename.fold(identity[String])(_.renamer), destRename.renamer)
            case (destRename, Some(srcRename)) =>
              planCoproductTransformationWithModifiedNames(source, dest, srcRename.renamer, destRename.fold(identity[String])(_.renamer))
            case _ =>
              planCoproductTransformation(source, dest)
          }

        case (source: Structure.Singleton, dest: Structure.Singleton)
            // ayy lmao
            if PlanFlags.current.source.get[Flag.Effect.Rename].fold(identity[String])(_.renamer)(source.name) == PlanFlags.current.dest
              .get[Flag.Effect.Rename]
              .fold(identity[String])(_.renamer)(dest.name) =>
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
  )(using Quotes, Depth, Context.Of[F], PlanFlags) = {
    import quotes.reflect.*

    val fieldPlans = dest.fields.map { (destField, destFieldStruct) =>
      val plan =
        source.fields
          .andThen(sourceStruct =>
            PlanFlags.current.transition(Step.Field(destField), Step.Field(destField)).locally {
              FieldPlan(destField, recurse(sourceStruct, destFieldStruct))
            }
          )
          .applyOrElse(
            destField,
            destField =>
              FieldPlan.empty(
                Plan.Error(
                  Structure.of[Nothing](source.path),
                  destFieldStruct,
                  ErrorMessage.NoFieldFound(destField, destFieldStruct.tpe, source.tpe),
                  None
                )
              )
          )

      destField -> plan

    }
    Plan.BetweenProducts(source, dest, fieldPlans)
  }

  private def planTupleTransformation[F <: Fallible](
    source: Structure.Tuple,
    dest: Structure.Tuple
  )(using Quotes, Depth, Context.Of[F], PlanFlags) = {
    val plans = dest.elements.zipWithIndex.map { (destFieldStruct, index) =>
      source.elements
        .andThen(sourceStruct =>
          PlanFlags.current.transition(Step.TupleElement(index), Step.TupleElement(index)).locally {
            recurse(sourceStruct, destFieldStruct)
          }
        )
        .applyOrElse(
          index,
          index =>
            Plan.Error(
              Structure.of[Nothing](source.path),
              destFieldStruct,
              ErrorMessage.NoFieldFoundAtIndex(index, source.tpe),
              None
            )
        )
    }.toVector

    Plan.BetweenTuples(source, dest, plans)
  }

  private def planTupleProductTransformation[F <: Fallible](
    source: Structure.Tuple,
    dest: Structure.Product
  )(using Quotes, Depth, Context.Of[F], PlanFlags) = {
    val plans = dest.fields.zipWithIndex.map {
      case (fieldName -> destFieldStruct, index) =>
        val plan = source.elements
          .andThen(sourceStruct =>
            PlanFlags.current.transition(Step.TupleElement(index), Step.Field(fieldName)).locally {
              recurse(sourceStruct, destFieldStruct)
            }
          )
          .applyOrElse(
            index,
            index =>
              Plan.Error(
                Structure.of[Nothing](source.path),
                destFieldStruct,
                ErrorMessage.NoFieldFoundAtIndex(index, source.tpe),
                None
              )
          )

        fieldName -> plan
    }.to(VectorMap)
    Plan.BetweenTupleProduct(source, dest, plans)
  }

  private def planTupleFunctionTransformation[F <: Fallible](
    source: Structure.Tuple,
    dest: Structure.Function
  )(using Quotes, Depth, Context.Of[F], PlanFlags) = {
    val plans = dest.args.zipWithIndex.map {
      case (fieldName -> destFieldStruct, index) =>
        val plan = source.elements
          .andThen(sourceStruct =>
            PlanFlags.current.transition(Step.TupleElement(index), Step.Field(fieldName)).locally {
              recurse(sourceStruct, destFieldStruct)
            }
          )
          .applyOrElse(
            index,
            index =>
              Plan.Error(
                Structure.of[Nothing](source.path),
                destFieldStruct,
                ErrorMessage.NoFieldFoundAtIndex(index, source.tpe),
                None
              )
          )

        fieldName -> plan
    }.to(VectorMap)

    Plan.BetweenTupleFunction(source, dest, plans)
  }

  private def planProductTupleTransformation[F <: Fallible](
    source: Structure.Product,
    dest: Structure.Tuple
  )(using Quotes, Depth, Context.Of[F], PlanFlags) = {
    val sourceFields = source.fields.toVector
    val plans = dest.elements.zipWithIndex.map { (destFieldStruct, index) =>
      sourceFields
        .andThen((sourceName, sourceStruct) =>
          PlanFlags.current.transition(Step.Field(sourceName), Step.TupleElement(index)).locally {
            FieldPlan(sourceName, recurse(sourceStruct, destFieldStruct))
          }
        )
        .applyOrElse(
          index,
          index =>
            FieldPlan.empty(
              Plan.Error(
                Structure.of[Nothing](source.path),
                destFieldStruct,
                ErrorMessage.NoFieldFoundAtIndex(index, source.tpe),
                None
              )
            )
        )
    }.toVector

    Plan.BetweenProductTuple(source, dest, plans)
  }

  private def planProductFunctionTransformation[F <: Fallible](
    source: Structure.Product,
    dest: Structure.Function
  )(using Quotes, Depth, Context.Of[F], PlanFlags) = {
    val argPlans = dest.args.map { (destField, destFieldStruct) =>
      val plan = source.fields
        .andThen(sourceStruct =>
          PlanFlags.current.transition(Step.Field(destField), Step.Field(destField)).locally {
            FieldPlan(destField, recurse(sourceStruct, destFieldStruct))
          }
        )
        .applyOrElse(
          destField,
          destField =>
            FieldPlan.empty(
              Plan.Error(
                Structure.of[Nothing](source.path),
                destFieldStruct,
                ErrorMessage.NoFieldFound(destField, destFieldStruct.tpe, source.tpe),
                None
              )
            )
        )
      destField -> plan
    }
    Plan.BetweenProductFunction(source, dest, argPlans)
  }

  private def planCoproductTransformation[F <: Fallible](
    source: Structure.Coproduct,
    dest: Structure.Coproduct
  )(using Quotes, Depth, Context.Of[F], PlanFlags) = {
    val casePlans = source.children.map { (sourceName, sourceCaseStruct) =>
      dest.children
        .andThen(destCaseStruct =>
          PlanFlags.current.transition(Step.Case(sourceCaseStruct.tpe), Step.Case(destCaseStruct.tpe)).locally {
            recurse(sourceCaseStruct, destCaseStruct)
          }
        )
        .applyOrElse(
          sourceName,
          sourceName =>
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
          while !owner.isNoSymbol do {
            if owner == transformerSymbol then boundary.break(Plan.Error.from(plan, ErrorMessage.LoopingTransformerDetected, None))
            owner = owner.maybeOwner
          }
          plan
      case _ => plan

  }

  object BetweenFallibleNonFallible {
    def unapply[F <: Fallible](
      structs: (Structure, Structure)
    )(using Quotes, Depth, Context.Of[F], PlanFlags): Option[Plan[Erroneous, F]] =
      PartialFunction.condOpt(Context.current *: structs) {
        case (ctx: Context.PossiblyFallible[f], source @ Wrapped(tpe, _, path, underlying), dest) =>
          // needed for the recurse call to return Plan[Erroneous, Nothing]
          val plan =
            ctx.toTotal.locally {
              Plan.BetweenFallibleNonFallible(
                source,
                dest,
                PlanFlags.current.transition(Step.Element, Passthrough).locally {
                  recurse(underlying, dest)
                }
              )
            }

          // the compiler needs a bit more encouragement to be sure that the plan we construct has a fallibility of F
          // Context.PossiblyFallible is defined with a type F = Fallible so we can deduce that ctx.F =:= Fallible =:= F
          ctx.reifyPlan[F](plan)
      }

  }

  object BetweenFallibles {
    def unapply[F <: Fallible](
      structs: (Structure, Structure)
    )(using Quotes, Depth, Context.Of[F], PlanFlags): Option[Plan[Erroneous, F]] =
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
              PlanFlags.current.transition(Step.Element, Step.Element).locally {
                recurse(underlying, dest)
              }
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
              PlanFlags.current.transition(Step.Element, Step.Element).locally {
                recurse(underlying, dest)
              }
            )
          }
      }
  }

  // POC of field renames and how to handle ambiguities
  private def planProductTransformationWithModifiedNames[F <: Fallible](
    source: Structure.Product,
    dest: Structure.Product,
    transformSrcName: String => String,
    transformDestName: String => String
  )(using Quotes, Depth, Context.Of[F], PlanFlags) = {
    // keys to transformed keys
    val destAmbiguities = dest.fields.keys.groupBy(transformDestName).filter((_, ambs) => ambs.size > 1)
    val sourceAmbiguities = source.fields.keys.groupBy(transformSrcName).filter((_, ambs) => ambs.size > 1)

    val transformedSource =
      source.fields
        .map((srcField, srcFieldStruct) => transformSrcName(srcField) -> (srcField, srcFieldStruct))

    val fieldPlans = dest.fields.map { (destField, destFieldStruct) =>
      val transformedDestField = transformDestName(destField)
      val destAmbs = destAmbiguities.getOrElse(transformedDestField, Vector.empty)
      val sourceAmbs = sourceAmbiguities.getOrElse(transformedDestField, Vector.empty)

      // TODO: Are those source and dest structs passed in correctly?
      if destAmbs.nonEmpty then
        destField -> FieldPlan.empty(
          Plan.Error(source, destFieldStruct, ErrorMessage.AmbiguousFieldTransformations(dest.tpe, destField, transformedDestField, destAmbs), None)
        )
      else if sourceAmbs.nonEmpty then
        destField -> FieldPlan.empty(
          Plan.Error(
            source,
            destFieldStruct,
            ErrorMessage.AmbiguousFieldTransformations(source.tpe, destField, transformSrcName(destField), sourceAmbs),
            None
          )
        )
      else {
        val plan =
          transformedSource
            .get(transformedDestField)
            .map((srcField, srcStruct) =>
              PlanFlags.current.transition(Step.Field(srcField), Step.Field(destField)).locally {
                FieldPlan(srcField, recurse(srcStruct, destFieldStruct))
              }
            )
            .getOrElse(
              FieldPlan.empty(
                Plan.Error(
                  Structure.of[Nothing](source.path),
                  destFieldStruct,
                  ErrorMessage.NoFieldFound(transformedDestField, destFieldStruct.tpe, source.tpe),
                  None
                )
              )
            )

        destField -> plan
      }
    }
    Plan.BetweenProducts(source, dest, fieldPlans)
  }

  private def planCoproductTransformationWithModifiedNames[F <: Fallible](
    source: Structure.Coproduct,
    dest: Structure.Coproduct,
    transformSrcName: String => String,
    transformDestName: String => String
  )(using Quotes, Depth, Context.Of[F], PlanFlags) = {
    // keys to transformed keys
    val destAmbiguities = dest.children.keys.toVector.groupBy(transformDestName).filter((_, ambs) => ambs.size > 1)
    val sourceAmbiguities = source.children.keys.toVector.groupBy(transformSrcName).filter((_, ambs) => ambs.size > 1)

    val transformedDest =
      dest.children
        .map((srcField, srcFieldStruct) => transformSrcName(srcField) -> srcFieldStruct)

    val plans = source.children.map { (sourceName, sourceCaseStruct) =>
      val transformedSrc = transformSrcName(sourceName)
      val destAmbs = destAmbiguities.getOrElse(transformedSrc, Vector.empty)
      val sourceAmbs = sourceAmbiguities.getOrElse(transformedSrc, Vector.empty)

      // TODO: Are those source and dest structs passed in correctly?
      if sourceAmbs.nonEmpty then
        Plan.Error(
          sourceCaseStruct,
          Structure.of[Any](dest.path),
          ErrorMessage.AmbiguousCaseTransformations(source.tpe, sourceName, transformedSrc, sourceAmbs),
          None
        )
      else if destAmbs.nonEmpty then
        Plan.Error(
          sourceCaseStruct,
          Structure.of[Any](dest.path),
          ErrorMessage.AmbiguousCaseTransformations(source.tpe, sourceName, transformedSrc, sourceAmbs),
          None
        )
      else {
        val plan =
          transformedDest
            .get(transformedSrc)
            .map(destCaseStruct =>
              PlanFlags.current.transition(Step.Case(sourceCaseStruct.tpe), Step.Case(destCaseStruct.tpe)).locally {
                recurse(sourceCaseStruct, destCaseStruct)
              }
            )
            .getOrElse(
              Plan.Error(
                sourceCaseStruct,
                Structure.of[Any](dest.path),
                ErrorMessage.NoChildFound(transformedSrc, dest.tpe),
                None
              )
            )

        plan
      }
    }
    Plan.BetweenCoproducts(source, dest, plans.toVector)
  }
}
