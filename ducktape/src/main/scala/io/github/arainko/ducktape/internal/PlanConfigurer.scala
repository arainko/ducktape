package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.internal.Configuration.Instruction

import scala.quoted.*

private[ducktape] object PlanConfigurer {
  import Plan.*

  def run[F <: Fallible](
    plan: Plan[Plan.Error, F],
    configs: List[Configuration.Instruction[F]]
  )(using Quotes): Plan.Reconfigured[F] = {
    def configureSingle(
      plan: Plan[Plan.Error, F],
      config: Configuration.Instruction[F]
    )(using Quotes, Accumulator[Plan.Error], Accumulator[(Path, Side)], Accumulator[ConfigWarning]): Plan[Plan.Error, F] = {

      def recurse(
        current: Plan[Plan.Error, F],
        segments: List[Path.Segment],
        parent: Plan[Plan.Error, F] | None.type
      )(using Quotes): Plan[Plan.Error, F] = {
        segments match {
          case (segment @ Path.Segment.Field(_, fieldName)) :: tail =>
            current match {
              case plan @ BetweenProducts(sourceTpe, destTpe, fieldPlans) =>
                val fieldPlan =
                  fieldPlans
                    .get(fieldName)
                    .map(fieldPlan => recurse(fieldPlan, tail, plan))
                    .getOrElse(Plan.Error.from(plan, ErrorMessage.InvalidFieldAccessor(fieldName, config.span), None))

                plan.copy(fieldPlans = fieldPlans.updated(fieldName, fieldPlan))

              case plan @ BetweenTupleProduct(source, dest, plans) if config.side.isDest =>
                plans
                  .get(fieldName)
                  .map(fieldPlan => plan.copy(plans = plans.updated(fieldName, recurse(fieldPlan, tail, plan))))
                  .getOrElse(Plan.Error.from(plan, ErrorMessage.InvalidFieldAccessor(fieldName, config.span), None))

              case plan @ BetweenProductTuple(source, dest, plans) if config.side.isSource =>
                // TODO: try to come up with something nicer? this is meh
                source.fields.keys
                  .zip(plans)
                  .zipWithIndex
                  .collectFirst {
                    case (`fieldName`, fieldPlan) -> index =>
                      plan.copy(plans = plans.updated(index, recurse(fieldPlan, tail, plan)))
                  }
                  .getOrElse(Plan.Error.from(plan, ErrorMessage.InvalidFieldAccessor(fieldName, config.span), None))

              case plan @ BetweenProductFunction(sourceTpe, destTpe, argPlans) =>
                val argPlan =
                  argPlans
                    .get(fieldName)
                    .map(argPlan => recurse(argPlan, tail, plan))
                    .getOrElse(Plan.Error.from(plan, ErrorMessage.InvalidArgAccessor(fieldName, config.span), None))

                plan.copy(argPlans = argPlans.updated(fieldName, argPlan))

              case plan @ BetweenTupleFunction(source, dest, argPlans) if config.side.isDest =>
                argPlans
                  .get(fieldName)
                  .map(argPlan => plan.copy(argPlans = argPlans.updated(fieldName, recurse(argPlan, tail, plan))))
                  .getOrElse(Plan.Error.from(plan, ErrorMessage.InvalidArgAccessor(fieldName, config.span), None))

              case other => invalidPathSegment(config, other, segment)
            }

          case (segment @ Path.Segment.TupleElement(_, index)) :: tail =>
            Logger.debug(s"Matched tupleElement with index of $index")

            current match {
              case plan @ BetweenTuples(source, dest, plans) =>
                Logger.debug(ds"Matched $plan")
                plans
                  .lift(index)
                  .map(elemPlan => plan.copy(plans = plans.updated(index, recurse(elemPlan, tail, plan))))
                  .getOrElse(
                    Plan.Error
                      .from(plan, ErrorMessage.InvalidTupleAccesor(index, config.span), None)
                  )

              case plan @ BetweenProductTuple(source, dest, plans) if config.side.isDest =>
                Logger.debug(ds"Matched $plan")
                plans
                  .lift(index)
                  .map(elemPlan => plan.copy(plans = plans.updated(index, recurse(elemPlan, tail, plan))))
                  .getOrElse(
                    Plan.Error
                      .from(plan, ErrorMessage.InvalidTupleAccesor(index, config.span), None)
                  )

              case plan @ BetweenTupleProduct(source, dest, plans) if config.side.isSource =>
                Logger.debug(ds"Matched $plan")
                plans.toVector
                  .lift(index)
                  .map((name, fieldPlan) => plan.copy(plans = plans.updated(name, recurse(fieldPlan, tail, plan))))
                  .getOrElse(
                    Plan.Error
                      .from(plan, ErrorMessage.InvalidTupleAccesor(index, config.span), None)
                  )

              case plan @ BetweenTupleFunction(source, dest, plans) if config.side.isSource =>
                Logger.debug(ds"Matched $plan")
                plans.toVector
                  .lift(index)
                  .map((name, fieldPlan) => plan.copy(argPlans = plans.updated(name, recurse(fieldPlan, tail, plan))))
                  .getOrElse(
                    Plan.Error
                      .from(plan, ErrorMessage.InvalidTupleAccesor(index, config.span), None)
                  )

              case other =>
                Logger.debug(s"Failing with invalid path segment on node: ${other.getClass.getSimpleName}")
                invalidPathSegment(config, other, segment)
            }

          case (segment @ Path.Segment.Case(tpe)) :: tail =>
            current match {
              // BetweenNonOptionOption keeps the same type as its source so we passthrough it when traversing source nodes
              case plan: BetweenNonOptionOption[Plan.Error, F] if config.side.isSource =>
                plan.copy(plan = recurse(plan.plan, segments, plan))

              case plan @ BetweenCoproducts(sourceTpe, destTpe, casePlans) =>
                def sideTpe(plan: Plan[Plan.Error, Fallible]) =
                  if (config.side.isSource) plan.source.tpe.repr else plan.dest.tpe.repr

                casePlans.zipWithIndex
                  .find((plan, _) => tpe.repr =:= sideTpe(plan))
                  .map((casePlan, idx) => plan.copy(casePlans = casePlans.updated(idx, recurse(casePlan, tail, plan))))
                  .getOrElse(Plan.Error.from(plan, ErrorMessage.InvalidCaseAccessor(tpe, config.span), None))

              case other => invalidPathSegment(config, other, segment)
            }

          case (segment @ Path.Segment.Element(tpe)) :: tail =>
            current match {
              case p @ BetweenCollections(_, _, plan) =>
                p.copy(plan = recurse(plan, tail, p))

              case p @ BetweenOptions(_, _, plan) =>
                p.copy(plan = recurse(plan, tail, p))

              case p @ BetweenNonOptionOption(_, _, plan) =>
                p.copy(plan = recurse(plan, tail, p))

              case other => invalidPathSegment(config, other, segment)
            }

          case Nil =>
            configurePlan(config, current, parent)
        }
      }

      // check if a Case config ends with a `.at` segment, otherwise weird things happen
      if config.side.isSource && config.path.segments.lastOption.exists(!_.isInstanceOf[Path.Segment.Case]) then
        Plan.Error.from(plan, ErrorMessage.SourceConfigDoesntEndWithCaseSegment(config.span), None)
      else recurse(plan, config.path.segments.toList, None)
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
    current: Plan[Error, F],
    parent: Plan[Plan.Error, F] | None.type
  )(using Quotes, Accumulator[Plan.Error], Accumulator[(Path, Side)], Accumulator[ConfigWarning]) = {
    Logger.debug(ds"Configuring plan $current with $config")

    config match {
      case cfg: (Configuration.Instruction.Static[F] | Configuration.Instruction.Dynamic[F]) =>
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
    plan: Plan[Error, Fallible],
    segment: Path.Segment
  ): Plan.Error =
    plan match {
      case suppressed: Plan.Error =>
        Plan.Error.from(plan, ErrorMessage.InvalidPathSegment(segment, config.side, config.span), Some(suppressed))
      case other =>
        Plan.Error.from(plan, ErrorMessage.InvalidPathSegment(segment, config.side, config.span), None)
    }

  private def staticOrDynamic[F <: Fallible](
    instruction: Configuration.Instruction.Static[F] | Configuration.Instruction.Dynamic[F],
    current: Plan[Plan.Error, F],
    parent: Plan[Plan.Error, F] | None.type
  )(using Quotes, Accumulator[Plan.Error], Accumulator[(Path, Side)], Accumulator[ConfigWarning]) = {
    instruction match {
      case static: Configuration.Instruction.Static[F] =>
        current.configureIfValid(static, static.config)

      case dynamic: Configuration.Instruction.Dynamic[F] =>
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
    plan: Plan[Plan.Error, F],
    modifier: Configuration.Instruction.Regional,
    parent: Plan[Plan.Error, F] | None.type
  )(using Quotes, Accumulator[Plan.Error], Accumulator[(Path, Side)], Accumulator[ConfigWarning]): Plan[Plan.Error, F] =
    plan match {
      case plan: Upcast => plan

      case plan: UserDefined[F] => plan

      case plan: Derived[F] => plan

      case plan: Configured[F] => plan

      case plan: BetweenProductFunction[Plan.Error, F] =>
        plan.copy(argPlans = plan.argPlans.transform((_, argPlan) => regional(argPlan, modifier, plan)))

      case plan: BetweenTupleFunction[Plan.Error, F] =>
        plan.copy(argPlans = plan.argPlans.transform((_, argPlan) => regional(argPlan, modifier, plan)))

      case plan: BetweenUnwrappedWrapped => plan

      case plan: BetweenWrappedUnwrapped => plan

      case plan: BetweenSingletons => plan

      case plan: BetweenProducts[Plan.Error, F] =>
        plan.copy(fieldPlans = plan.fieldPlans.transform((_, fieldPlan) => regional(fieldPlan, modifier, plan)))

      case plan: BetweenProductTuple[Plan.Error, F] =>
        plan.copy(plans = plan.plans.map(fieldPlan => regional(fieldPlan, modifier, plan)))

      case plan: BetweenTupleProduct[Plan.Error, F] =>
        plan.copy(plans = plan.plans.transform((_, fieldPlan) => regional(fieldPlan, modifier, plan)))

      case plan: BetweenTuples[Plan.Error, F] =>
        plan.copy(plans = plan.plans.map(fieldPlan => regional(fieldPlan, modifier, plan)))

      case plan: BetweenCoproducts[Plan.Error, F] =>
        plan.copy(casePlans = plan.casePlans.map(regional(_, modifier, plan)))

      case plan: BetweenOptions[Plan.Error, F] =>
        plan.copy(plan = regional(plan.plan, modifier, plan))

      case plan: BetweenNonOptionOption[Plan.Error, F] =>
        plan.copy(plan = regional(plan.plan, modifier, plan))

      case plan: BetweenCollections[Plan.Error, F] =>
        plan.copy(plan = regional(plan.plan, modifier, plan))

      case plan: Error =>
        // TODO: Detect when a regional config doesn't do anything and emit an error
        modifier.modifier(parent, plan) match {
          case config: Configuration[F] => plan.configureIfValid(modifier, config)
          case other: plan.type         => other
        }
    }

  // TODO: Support tuple-to-tuple, product-to-tuple?
  private def bulk[F <: Fallible](
    current: Plan[Plan.Error, F],
    instruction: Configuration.Instruction.Bulk
  )(using Quotes, Accumulator[Plan.Error], Accumulator[(Path, Side)], Accumulator[ConfigWarning]): Plan[Error, F] = {

    enum IsAnythingModified {
      case Yes, No
    }

    var isAnythingModified = IsAnythingModified.No

    def updatePlan(
      parent: Plan.BetweenProducts[Plan.Error, F] | Plan.BetweenProductFunction[Plan.Error, F] |
        Plan.BetweenTupleProduct[Plan.Error, F]
    )(
      name: String,
      plan: Plan[Plan.Error, F]
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
        case func: Plan.BetweenProductFunction[Plan.Error, F] =>
          val updatedArgPlans = func.argPlans.transform(updatePlan(func))
          func.copy(argPlans = updatedArgPlans)
        case prod: Plan.BetweenProducts[Plan.Error, F] =>
          val updatedFieldPlans = prod.fieldPlans.transform(updatePlan(prod))
          prod.copy(fieldPlans = updatedFieldPlans)
        case prodTuple: Plan.BetweenTupleProduct[Plan.Error, F] =>
          val updatedFieldPlans = prodTuple.plans.transform(updatePlan(prodTuple))
          prodTuple.copy(plans = updatedFieldPlans)
      }
      .toRight("This config only works when applied to name-wise based product transformations (product-to-product, tuple-to-product, product-via-function)")
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

  extension [F <: Fallible](currentPlan: Plan[Plan.Error, F]) {

    private def configureIfValid(
      instruction: Configuration.Instruction[F],
      config: Configuration[F]
    )(using
      quotes: Quotes,
      errors: Accumulator[Plan.Error],
      successes: Accumulator[(Path, Side)],
      warnings: Accumulator[ConfigWarning]
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
      plan: Plan[Error, Fallible],
      accumulator: List[Plan.Configured[Fallible]]
    ): List[Plan.Configured[Fallible]] =
      plan match {
        case configured: Plan.Configured[Fallible] => configured :: accumulator
        case other                                 => accumulator
      }
  }
}
