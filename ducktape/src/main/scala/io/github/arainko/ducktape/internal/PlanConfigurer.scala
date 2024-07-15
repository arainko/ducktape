package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.internal.Configuration.Instruction

import scala.quoted.*

private[ducktape] object PlanConfigurer {
  import Plan.*

  def run[F <: Fallible](
    plan: Plan[Erroneous, F],
    configs: List[Configuration.Instruction[F]]
  )(using Quotes, Context): Plan.Reconfigured[F] = {
    def configureSingle(
      plan: Plan[Erroneous, F],
      config: Configuration.Instruction[F]
    )(using Quotes, Accumulator[Plan.Error], Accumulator[(Path, Side)], Accumulator[ConfigWarning]): Plan[Erroneous, F] = {

      def recurse[F <: Fallible](
        current: Plan[Erroneous, F],
        segments: List[Path.Segment],
        parent: Plan[Erroneous, Fallible] | None.type,
        config: Configuration.Instruction[F]
      )(using Quotes): Plan[Erroneous, F] = {
        def traverseBetweenNotFallible(
          parent: BetweenFallibleNonFallible[Erroneous],
          plan: Plan[Erroneous, Nothing],
          tail: List[Path.Segment]
        ) =
          ConfigInstructionRefiner.run(config) match
            case None =>
              Plan.Error.from(parent, ErrorMessage.FallibleConfigNotPermitted(config.span, config.side), None)
            case nonFallible: Configuration.Instruction[Nothing] =>
              parent.copy(plan = recurse(plan, tail, parent, nonFallible))

        segments match {
          case (segment @ Path.Segment.Field(_, fieldName)) :: tail =>
            current match {
              // passthrough BetweenFallibles, the dest is just a normal field in this case
              case parent @ BetweenFallibleNonFallible(source, dest, plan) if config.side.isDest =>
                traverseBetweenNotFallible(parent, plan, segments)

              // passthrough BetweenFallibles, the dest is just a normal field in this case
              case parent @ BetweenFallibles(source, dest, mode, plan) if config.side.isDest =>
                parent.copy(plan = recurse(plan, segments, parent, config))

              case parent @ BetweenProducts(sourceTpe, destTpe, fieldPlans) =>
                val fieldPlan =
                  fieldPlans
                    .get(fieldName)
                    .map(fieldPlan => recurse(fieldPlan, tail, parent, config))
                    .getOrElse(Plan.Error.from(parent, ErrorMessage.InvalidFieldAccessor(fieldName, config.span), None))

                parent.copy(fieldPlans = fieldPlans.updated(fieldName, fieldPlan))

              case parent @ BetweenTupleProduct(source, dest, plans) if config.side.isDest =>
                plans
                  .get(fieldName)
                  .map(fieldPlan => parent.copy(plans = plans.updated(fieldName, recurse(fieldPlan, tail, parent, config))))
                  .getOrElse(Plan.Error.from(parent, ErrorMessage.InvalidFieldAccessor(fieldName, config.span), None))

              case parent @ BetweenProductTuple(source, dest, plans) if config.side.isSource =>
                val sourceFields = source.fields.keys

                // basically, find the index of `fieldName`
                plans.zipWithIndex.collectFirst {
                  case (fieldPlan, index @ sourceFields(`fieldName`)) =>
                    parent.copy(plans = plans.updated(index, recurse(fieldPlan, tail, parent, config)))
                }.getOrElse(Plan.Error.from(parent, ErrorMessage.InvalidFieldAccessor(fieldName, config.span), None))

              case parent @ BetweenProductFunction(sourceTpe, destTpe, argPlans) =>
                val argPlan =
                  argPlans
                    .get(fieldName)
                    .map(argPlan => recurse(argPlan, tail, parent, config))
                    .getOrElse(Plan.Error.from(parent, ErrorMessage.InvalidArgAccessor(fieldName, config.span), None))

                parent.copy(argPlans = argPlans.updated(fieldName, argPlan))

              case parent @ BetweenTupleFunction(source, dest, argPlans) if config.side.isDest =>
                argPlans
                  .get(fieldName)
                  .map(argPlan => parent.copy(argPlans = argPlans.updated(fieldName, recurse(argPlan, tail, parent, config))))
                  .getOrElse(Plan.Error.from(parent, ErrorMessage.InvalidArgAccessor(fieldName, config.span), None))

              case other => invalidPathSegment(config, other, segment)
            }

          case (segment @ Path.Segment.TupleElement(_, index)) :: tail =>
            Logger.debug(s"Matched tupleElement with index of $index")

            current match {
              // passthrough BetweenFallibles, the dest is just a tuple elem in this case
              case parent @ BetweenFallibleNonFallible(source, dest, plan) if config.side.isDest =>
                traverseBetweenNotFallible(parent, plan, segments)

              // passthrough BetweenFallibles, the dest is just a tuple elem in this case
              case parent @ BetweenFallibles(source, dest, mode, plan) if config.side.isDest =>
                parent.copy(plan = recurse(plan, segments, parent, config))

              case parent @ BetweenTuples(source, dest, plans) =>
                Logger.debug(ds"Matched $parent")
                plans
                  .lift(index)
                  .map(elemPlan => parent.copy(plans = plans.updated(index, recurse(elemPlan, tail, plan, config))))
                  .getOrElse(
                    Plan.Error
                      .from(plan, ErrorMessage.InvalidTupleAccesor(index, config.span), None)
                  )

              case parent @ BetweenProductTuple(source, dest, plans) if config.side.isDest =>
                Logger.debug(ds"Matched $parent")
                plans
                  .lift(index)
                  .map(elemPlan => parent.copy(plans = plans.updated(index, recurse(elemPlan, tail, parent, config))))
                  .getOrElse(
                    Plan.Error
                      .from(parent, ErrorMessage.InvalidTupleAccesor(index, config.span), None)
                  )

              case parent @ BetweenTupleProduct(source, dest, plans) if config.side.isSource =>
                Logger.debug(ds"Matched $parent")
                plans.toVector
                  .lift(index)
                  .map((name, fieldPlan) => parent.copy(plans = plans.updated(name, recurse(fieldPlan, tail, parent, config))))
                  .getOrElse(
                    Plan.Error
                      .from(parent, ErrorMessage.InvalidTupleAccesor(index, config.span), None)
                  )

              case parent @ BetweenTupleFunction(source, dest, plans) if config.side.isSource =>
                Logger.debug(ds"Matched $parent")
                plans.toVector
                  .lift(index)
                  .map((name, fieldPlan) => parent.copy(argPlans = plans.updated(name, recurse(fieldPlan, tail, parent, config))))
                  .getOrElse(
                    Plan.Error
                      .from(parent, ErrorMessage.InvalidTupleAccesor(index, config.span), None)
                  )

              case other =>
                Logger.debug(s"Failing with invalid path segment on node: ${other.getClass.getSimpleName}")
                invalidPathSegment(config, other, segment)
            }

          case (segment @ Path.Segment.Case(tpe)) :: tail =>
            current match {
              // BetweenNonOptionOption keeps the same type as its source so we passthrough it when traversing source nodes
              case parent: BetweenNonOptionOption[Erroneous, F] if config.side.isSource =>
                parent.copy(plan = recurse(parent.plan, segments, parent, config))

              case parent @ BetweenCoproducts(sourceTpe, destTpe, casePlans) =>
                def sideTpe(plan: Plan[Erroneous, Fallible]) =
                  if config.side.isSource then plan.source.tpe.repr else plan.dest.tpe.repr

                casePlans.zipWithIndex
                  .find((plan, _) => tpe.repr =:= sideTpe(plan))
                  .map((casePlan, idx) =>
                    parent.copy(casePlans = casePlans.updated(idx, recurse(casePlan, tail, parent, config)))
                  )
                  .getOrElse(Plan.Error.from(parent, ErrorMessage.InvalidCaseAccessor(tpe, config.span), None))

              case other => invalidPathSegment(config, other, segment)
            }

          case (segment @ Path.Segment.Element(tpe)) :: tail =>
            current match {
              case parent @ BetweenCollections(_, _, plan) =>
                parent.copy(plan = recurse(plan, tail, parent, config))

              case parent @ BetweenOptions(_, _, plan) =>
                parent.copy(plan = recurse(plan, tail, parent, config))

              case parent @ BetweenNonOptionOption(_, _, plan) =>
                parent.copy(plan = recurse(plan, tail, parent, config))

              case parent @ BetweenFallibleNonFallible(source, dest, plan) if config.side.isSource =>
                traverseBetweenNotFallible(parent, plan, tail)

              case parent @ BetweenFallibles(source, dest, mode, plan) if config.side.isSource =>
                parent.copy(plan = recurse(plan, tail, parent, config))

              case other => invalidPathSegment(config, other, segment)
            }

          case Nil =>
            configurePlan(config, current, parent)
        }
      }

      // check if a Case config ends with a `.at` segment, otherwise weird things happen
      if config.side.isSource && config.path.segments.lastOption.exists(!_.isInstanceOf[Path.Segment.Case]) then
        Plan.Error.from(plan, ErrorMessage.SourceConfigDoesntEndWithCaseSegment(config.span), None)
      else recurse(plan, config.path.segments.toList, None, config)
    }

    val (errors, successes, warnings, reconfiguredPlan) =
      Accumulator.use[Plan.Error]:
        Accumulator.use[(Path, Side)]:
          Accumulator.use[ConfigWarning]:
            configs.foldLeft(plan)(configureSingle) *: EmptyTuple

    Plan.Reconfigured(errors, successes, warnings, reconfiguredPlan)
  }

  private def configurePlan[F <: Fallible](
    config: Configuration.Instruction[F],
    current: Plan[Erroneous, F],
    parent: Plan[Erroneous, Fallible] | None.type
  )(using Quotes, Accumulator[Plan.Error], Accumulator[(Path, Side)], Accumulator[ConfigWarning], Context): Plan[Erroneous, F] = {
    Logger.debug(ds"Configuring plan $current with $config")

    config match {
      case cfg: (Configuration.Instruction.Static[F] | Configuration.Instruction.Dynamic) =>
        staticOrDynamic(cfg, current, parent)

      case instruction: Configuration.Instruction.Bulk =>
        bulk(current, instruction)

      case cfg @ Configuration.Instruction.Regional(path, side, modifier, span) =>
        regional(current, cfg, parent)

      case cfg @ Configuration.Instruction.Failed(path, side, message, span) =>
        Accumulator.append {
          Plan.Error.from(current, ErrorMessage.ConfigurationFailed(cfg), None)
        }
    }
  }

  private def invalidPathSegment(
    config: Configuration.Instruction[Fallible],
    plan: Plan[Erroneous, Fallible],
    segment: Path.Segment
  ): Plan.Error =
    plan match {
      case suppressed: Plan.Error =>
        Plan.Error.from(plan, ErrorMessage.InvalidPathSegment(segment, config.side, config.span), Some(suppressed))
      case other =>
        Plan.Error.from(plan, ErrorMessage.InvalidPathSegment(segment, config.side, config.span), None)
    }

  private def staticOrDynamic[F <: Fallible](
    instruction: Configuration.Instruction.Static[F] | Configuration.Instruction.Dynamic,
    current: Plan[Erroneous, F],
    parent: Plan[Erroneous, Fallible] | None.type
  )(using Quotes, Accumulator[Plan.Error], Accumulator[(Path, Side)], Accumulator[ConfigWarning], Context) = {
    instruction match {
      case static: Configuration.Instruction.Static[F] =>
        current.configureIfValid(static, static.config)

      case dynamic: Configuration.Instruction.Dynamic =>
        dynamic.config(parent) match {
          case Right(config) => current.configureIfValid(dynamic, config)
          case Left(errorMessage) =>
            val failed = Configuration.Instruction.Failed.from(dynamic, errorMessage)
            Accumulator.append {
              Plan.Error.from(current, ErrorMessage.ConfigurationFailed(failed), None)
            }
        }
    }
  }

  private def regional[F <: Fallible](
    plan: Plan[Erroneous, F],
    modifier: Configuration.Instruction.Regional,
    parent: Plan[Erroneous, Fallible] | None.type
  )(using Quotes, Accumulator[Plan.Error], Accumulator[(Path, Side)], Accumulator[ConfigWarning], Context): Plan[Erroneous, F] =
    plan match {
      case plan: Upcast => plan

      case plan: UserDefined[F] => plan

      case plan: Derived[F] => plan

      case plan: Configured[F] => plan

      case plan: BetweenProductFunction[Erroneous, F] =>
        plan.copy(argPlans = plan.argPlans.transform((_, argPlan) => regional(argPlan, modifier, plan)))

      case plan: BetweenTupleFunction[Erroneous, F] =>
        plan.copy(argPlans = plan.argPlans.transform((_, argPlan) => regional(argPlan, modifier, plan)))

      case plan: BetweenUnwrappedWrapped => plan

      case plan: BetweenWrappedUnwrapped => plan

      case plan: BetweenSingletons => plan

      case plan: BetweenProducts[Erroneous, F] =>
        plan.copy(fieldPlans = plan.fieldPlans.transform((_, fieldPlan) => regional(fieldPlan, modifier, plan)))

      case plan: BetweenProductTuple[Erroneous, F] =>
        plan.copy(plans = plan.plans.map(fieldPlan => regional(fieldPlan, modifier, plan)))

      case plan: BetweenTupleProduct[Erroneous, F] =>
        plan.copy(plans = plan.plans.transform((_, fieldPlan) => regional(fieldPlan, modifier, plan)))

      case plan: BetweenTuples[Erroneous, F] =>
        plan.copy(plans = plan.plans.map(fieldPlan => regional(fieldPlan, modifier, plan)))

      case plan: BetweenCoproducts[Erroneous, F] =>
        plan.copy(casePlans = plan.casePlans.map(regional(_, modifier, plan)))

      case plan: BetweenOptions[Erroneous, F] =>
        plan.copy(plan = regional(plan.plan, modifier, plan))

      case plan: BetweenNonOptionOption[Erroneous, F] =>
        plan.copy(plan = regional(plan.plan, modifier, plan))

      case plan: BetweenCollections[Erroneous, F] =>
        plan.copy(plan = regional(plan.plan, modifier, plan))

      case plan: BetweenFallibleNonFallible[Erroneous] =>
        plan.copy(plan = regional(plan.plan, modifier, plan))

      case plan @ BetweenFallibles(_, _, _, elemPlan) =>
        plan.copy(plan = regional(elemPlan, modifier, plan))

      case plan: Error =>
        // TODO: Detect when a regional config doesn't do anything and emit an error
        modifier.modifier(parent, plan) match {
          case config: Configuration[F] => plan.configureIfValid(modifier, config)
          case other: plan.type         => other
        }
    }

  // TODO: Support tuple-to-tuple, product-to-tuple?
  private def bulk[F <: Fallible](
    current: Plan[Erroneous, F],
    instruction: Configuration.Instruction.Bulk
  )(using Quotes, Accumulator[Plan.Error], Accumulator[(Path, Side)], Accumulator[ConfigWarning], Context): Plan[Erroneous, F] = {

    enum IsAnythingModified {
      case Yes, No
    }

    var isAnythingModified = IsAnythingModified.No

    def updatePlan(
      parent: Plan.BetweenProducts[Erroneous, F] | Plan.BetweenProductFunction[Erroneous, F] |
        Plan.BetweenTupleProduct[Erroneous, F]
    )(
      name: String,
      plan: Plan[Erroneous, F]
    )(using Quotes) = {
      instruction.modifier(parent, name, plan) match {
        case config: Configuration[Nothing] =>
          isAnythingModified = IsAnythingModified.Yes
          plan.configureIfValid(instruction, config)
        case p: plan.type => p
      }
    }

    PartialFunction
      .condOpt(current) {
        case func: Plan.BetweenProductFunction[Erroneous, F] =>
          val updatedArgPlans = func.argPlans.transform(updatePlan(func))
          func.copy(argPlans = updatedArgPlans)
        case prod: Plan.BetweenProducts[Erroneous, F] =>
          val updatedFieldPlans = prod.fieldPlans.transform(updatePlan(prod))
          prod.copy(fieldPlans = updatedFieldPlans)
        case prodTuple: Plan.BetweenTupleProduct[Erroneous, F] =>
          val updatedFieldPlans = prodTuple.plans.transform(updatePlan(prodTuple))
          prodTuple.copy(plans = updatedFieldPlans)
      }
      .toRight(
        "This config only works when applied to name-wise based product transformations (product-to-product, tuple-to-product, product-via-function)"
      )
      .filterOrElse(_ => isAnythingModified == IsAnythingModified.Yes, "Config option is not doing anything")
      .fold(
        errorMessage => {
          val failed = Configuration.Instruction.Failed.from(instruction, errorMessage)
          Accumulator.append {
            Plan.Error.from(current, ErrorMessage.ConfigurationFailed(failed), None)
          }
        },
        identity
      )
  }

  extension [F <: Fallible](currentPlan: Plan[Erroneous, F]) {

    private def configureIfValid(
      instruction: Configuration.Instruction[F],
      config: Configuration[F]
    )(using
      quotes: Quotes,
      errors: Accumulator[Plan.Error],
      successes: Accumulator[(Path, Side)],
      warnings: Accumulator[ConfigWarning],
      context: Context
    ) = {
      def isReplaceableBy(update: Configuration[F])(using Quotes) =
        update.tpe.repr <:< currentPlan.destPath.currentTpe.repr

      if isReplaceableBy(config) then
        val (path, _) =
          Accumulator.append {
            if instruction.side == Side.Dest then currentPlan.destPath -> instruction.side
            else currentPlan.sourcePath -> instruction.side
          }
        Accumulator.appendAll {
          ConfiguredCollector
            .run(currentPlan, Nil)
            .map(plan => ConfigWarning(plan.span, instruction.span, path))
        }
        Plan.Configured.from(currentPlan, config, instruction)
      else
        Accumulator.append {
          Plan.Error.from(
            currentPlan,
            ErrorMessage.InvalidConfiguration(
              config.tpe,
              currentPlan.destPath.currentTpe,
              instruction.side,
              instruction.span
            ),
            None
          )
        }
    }
  }

  private object ConfiguredCollector extends PlanTraverser[List[Plan.Configured[Fallible]]] {
    protected def foldOver(
      plan: Plan[Erroneous, Fallible],
      accumulator: List[Plan.Configured[Fallible]]
    ): List[Plan.Configured[Fallible]] =
      plan match {
        case configured: Plan.Configured[Fallible] => configured :: accumulator
        case other                                 => accumulator
      }
  }
}
