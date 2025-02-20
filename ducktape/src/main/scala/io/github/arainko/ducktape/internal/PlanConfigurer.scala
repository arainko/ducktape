package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.internal.Configuration.Instruction
import io.github.arainko.ducktape.internal.Path.Segment

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
              parent.update(recurse(_, tail, parent, nonFallible))

        def handleElement(
          segment: Path.Segment.Element,
          tail: List[Segment],
          current: Plan[Erroneous, F]
        ): Plan[Erroneous, F] =
          current match {
            case parent @ BetweenCollections(_, _, _, plan) =>
              parent.update(recurse(_, tail, parent, config))

            case parent @ BetweenOptions(_, _, plan) =>
              parent.update(recurse(_, tail, parent, config))

            case parent @ BetweenNonOptionOption(_, _, plan) =>
              parent.update(recurse(_, tail, parent, config))

            case parent @ BetweenFallibleNonFallible(source, dest, plan) if config.side.isSource =>
              traverseBetweenNotFallible(parent, plan, tail)

            case parent @ BetweenFallibles(source, dest, mode, plan) if config.side.isSource =>
              parent.update(recurse(_, tail, parent, config))

            case paren: Upcast =>
              // TODO: use paren.update
              recurse(paren.alt, segments, parent, config)

            case other => invalidPathSegment(config, other, segment)
          }

        def handleField(segment: Path.Segment.Field, tail: List[Segment], current: Plan[Erroneous, F]): Plan[Erroneous, F] =
          current match {
            // passthrough BetweenFallibles, the dest is just a normal field in this case
            case parent @ BetweenFallibleNonFallible(source, dest, plan) if config.side.isDest =>
              traverseBetweenNotFallible(parent, plan, segments)

            // passthrough BetweenFallibles, the dest is just a normal field in this case
            case parent @ BetweenFallibles(source, dest, mode, plan) if config.side.isDest =>
              parent.update(recurse(_, tail, parent, config))

            case parent @ BetweenProducts(sourceTpe, destTpe, fieldPlans) =>
              parent.updateOrElse(segment.name)(
                recurse(_, tail, parent, config),
                Plan.Error.from(parent, ErrorMessage.InvalidFieldAccessor(segment.name, config.span), None)
              )

            case parent @ BetweenTupleProduct(source, dest, plans) if config.side.isDest =>
              parent.updateByNameOrElse(segment.name)(
                recurse(_, tail, parent, config),
                Plan.Error.from(parent, ErrorMessage.InvalidFieldAccessor(segment.name, config.span), None)
              )

            case parent @ BetweenProductTuple(source, dest, plans) if config.side.isSource =>
              parent.updateByNameOrElse(segment.name)(
                recurse(_, tail, parent, config),
                Plan.Error.from(parent, ErrorMessage.InvalidFieldAccessor(segment.name, config.span), None)
              )

            case parent @ BetweenProductFunction(sourceTpe, destTpe, argPlans) =>
              parent.updateOrElse(segment.name)(
                recurse(_, tail, parent, config),
                Plan.Error.from(parent, ErrorMessage.InvalidArgAccessor(segment.name, config.span), None)
              )

            case parent @ BetweenTupleFunction(source, dest, argPlans) if config.side.isDest =>
              parent.updateByNameOrElse(segment.name)(
                recurse(_, tail, parent, config),
                Plan.Error.from(parent, ErrorMessage.InvalidArgAccessor(segment.name, config.span), None)
              )

            case paren: Upcast =>
              // TODO: use paren.update
              recurse(paren.alt, segments, parent, config)

            case other => invalidPathSegment(config, other, segment)
          }

        def handleTupleElement(
          segment: Path.Segment.TupleElement,
          tail: List[Segment],
          current: Plan[Erroneous, F]
        ): Plan[Erroneous, F] = {
          val index = segment.index
          Logger.debug(s"Matched tupleElement with index of $index")

          current match {
            // passthrough BetweenFallibles, the dest is just a tuple elem in this case
            case parent @ BetweenFallibleNonFallible(source, dest, plan) if config.side.isDest =>
              traverseBetweenNotFallible(parent, plan, segments)

            // passthrough BetweenFallibles, the dest is just a tuple elem in this case
            case parent @ BetweenFallibles(source, dest, mode, plan) if config.side.isDest =>
              parent.update(recurse(_, segments, parent, config))

            case parent @ BetweenTuples(source, dest, plans) =>
              Logger.debug(ds"Matched $parent")
              parent.updateByIndexOrElse(index)(
                recurse(_, tail, plan, config),
                Plan.Error.from(plan, ErrorMessage.InvalidTupleAccesor(index, config.span), None)
              )

            case parent @ BetweenProductTuple(source, dest, plans) if config.side.isDest =>
              Logger.debug(ds"Matched $parent")
              parent.updateByIndexOrElse(index)(
                recurse(_, tail, parent, config),
                Plan.Error.from(parent, ErrorMessage.InvalidTupleAccesor(index, config.span), None)
              )

            case parent @ BetweenTupleProduct(source, dest, plans) if config.side.isSource =>
              Logger.debug(ds"Matched $parent")
              parent.updateByIndexOrElse(index)(
                recurse(_, tail, parent, config),
                Plan.Error.from(parent, ErrorMessage.InvalidTupleAccesor(index, config.span), None)
              )

            case parent @ BetweenTupleFunction(source, dest, plans) if config.side.isSource =>
              Logger.debug(ds"Matched $parent")
              parent.updateByIndexOrElse(index)(
                recurse(_, tail, parent, config),
                Plan.Error.from(parent, ErrorMessage.InvalidTupleAccesor(index, config.span), None)
              )

            case paren: Upcast =>
              // TODO: use paren.update
              recurse(paren.alt, segments, parent, config)

            case other =>
              Logger.debug(s"Failing with invalid path segment on node: ${other.getClass.getSimpleName}")
              invalidPathSegment(config, other, segment)
          }
        }

        def handleCase(segment: Path.Segment.Case, tail: List[Segment], current: Plan[Erroneous, F]): Plan[Erroneous, F] = {
          val tpe = segment.tpe
          current match {
            // BetweenNonOptionOption keeps the same type as its source so we passthrough it when traversing source nodes
            case parent: BetweenNonOptionOption[Erroneous, F] if config.side.isSource =>
              parent.update(recurse(_, segments, parent, config))

            case parent @ BetweenCoproducts(sourceTpe, destTpe, casePlans) =>
              def sideTpe(plan: Plan[Erroneous, Fallible]) =
                if config.side.isSource then plan.source.tpe.repr else plan.dest.tpe.repr

              casePlans.zipWithIndex
                .find((plan, _) => tpe.repr =:= sideTpe(plan))
                .map((casePlan, idx) => parent.copy(casePlans = casePlans.updated(idx, recurse(casePlan, tail, parent, config))))
                .getOrElse(Plan.Error.from(parent, ErrorMessage.InvalidCaseAccessor(tpe, config.span), None))

            case paren: Upcast =>
              // TODO: use paren.update
              recurse(paren.alt, segments, parent, config)

            case other => invalidPathSegment(config, other, segment)
          }
        }

        segments match {
          case (segment @ Path.Segment.Field(_, _)) :: tail =>
            handleField(segment, tail, current)

          case (segment @ Path.Segment.TupleElement(_, _)) :: tail =>
            handleTupleElement(segment, tail, current)

          case (segment @ Path.Segment.Case(_)) :: tail =>
            handleCase(segment, tail, current)

          case (segment @ Path.Segment.Element(_)) :: tail =>
            handleElement(segment, tail, current)

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

      case cfg: Configuration.Instruction.Failed =>
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
          val updatedArgPlans = func.argPlans.transform((name, fieldPlan) => fieldPlan.update(updatePlan(func)(name, _)))
          func.copy(argPlans = updatedArgPlans)
        case prod: Plan.BetweenProducts[Erroneous, F] =>
          val updatedFieldPlans =
            prod.fieldPlans.transform((name, fieldPlan) => fieldPlan.update(updatePlan(prod)(name, _)))
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
      def isReplaceableBy(update: Configuration[F])(using Quotes) = {
        def checkDestTpe =
          Either
            .cond(
              update.destTpe.repr <:< currentPlan.destPath.currentTpe.repr,
              (),
              ErrorMessage.InvalidConfigurationDestType(
                config.destTpe,
                currentPlan.destPath.currentTpe,
                instruction.side,
                instruction.span
              )
            )

        def checkSourceTpe =
          Either.cond(
            update.sourceTpe.fold(true, tpe => currentPlan.sourcePath.narrowedCurrentTpe.repr <:< tpe.repr),
            (),
            ErrorMessage.InvalidConfigurationSourceType(
              config.sourceTpe.getOrElse(Type.of[Any]),
              currentPlan.sourcePath.narrowedCurrentTpe,
              instruction.side,
              instruction.span
            )
          )

        checkSourceTpe.zipRight(checkDestTpe)
      }

      isReplaceableBy(config) match {
        case Left(value) =>
          Accumulator.append {
            Plan.Error.from(
              currentPlan,
              value,
              None
            )
          }
        case Right(value) =>
          val (path, _) =
            Accumulator.append {
              if instruction.side == Side.Dest then currentPlan.destPath -> instruction.side
              else currentPlan.sourcePath -> instruction.side
            }
          Accumulator.appendAll {
            ConfiguredCollector
              .run(currentPlan, Nil)
              .filter(_.priority < instruction.priority)
              .map(plan => ConfigWarning(plan.span, instruction.span, path))
          }
          currentPlan match {
            case Plan.Configured(_, _, _, _, prio) => 
              if instruction.priority >= prio then Plan.Configured.fromInstruction(currentPlan, config, instruction) else currentPlan
            case _ => Plan.Configured.fromInstruction(currentPlan, config, instruction)
          }

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
