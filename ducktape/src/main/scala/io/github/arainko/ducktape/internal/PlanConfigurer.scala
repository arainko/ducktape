package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.internal.Configuration.Instruction

import scala.collection.mutable.Builder
import scala.quoted.*

private[ducktape] object PlanConfigurer {
  import Plan.*

  def run(plan: Plan[Plan.Error], configs: List[Configuration.Instruction])(using Quotes): Plan.Reconfigured = {
    def configureSingle(
      plan: Plan[Plan.Error],
      config: Configuration.Instruction
    )(using Quotes, Accumulator[Plan.Error], Accumulator[(Path, Side)]): Plan[Plan.Error] = {

      def recurse(
        current: Plan[Plan.Error],
        segments: List[Path.Segment],
        parent: Plan[Plan.Error] | None.type
      )(using Quotes): Plan[Plan.Error] = {
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

              case plan @ BetweenProductFunction(sourceTpe, destTpe, argPlans) =>
                val argPlan =
                  argPlans
                    .get(fieldName)
                    .map(argPlan => recurse(argPlan, tail, plan))
                    .getOrElse(Plan.Error.from(plan, ErrorMessage.InvalidArgAccessor(fieldName, config.span), None))

                plan.copy(argPlans = argPlans.updated(fieldName, argPlan))

              case other => invalidPathSegment(config, other, segment)
            }

          case (segment @ Path.Segment.Case(tpe)) :: tail =>
            current match {
              // BetweenNonOptionOption keeps the same type as its source so we passthrough it when traversing source nodes
              case plan: BetweenNonOptionOption[Plan.Error] if config.side.isSource =>
                plan.copy(plan = recurse(plan.plan, segments, plan))

              case plan @ BetweenCoproducts(sourceTpe, destTpe, casePlans) =>
                def sideTpe(plan: Plan[Plan.Error]) = if (config.side.isSource) plan.source.tpe.repr else plan.dest.tpe.repr

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

      recurse(plan, config.path.segments.toList, None)
    }

    val (errors, (successes, reconfiguredPlan)) =
      Accumulator.use[Plan.Error]:
        Accumulator.use[(Path, Side)]:
          configs.foldLeft(plan)(configureSingle)

    Plan.Reconfigured(errors, successes, reconfiguredPlan)
  }

  private def configurePlan(
    config: Configuration.Instruction,
    current: Plan[Error],
    parent: Plan[Plan.Error] | None.type
  )(using Quotes, Accumulator[Plan.Error], Accumulator[(Path, Side)]) = {
    config match {
      case cfg: (Configuration.Instruction.Static | Configuration.Instruction.Dynamic) =>
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

  private def invalidPathSegment(config: Configuration.Instruction, plan: Plan[Error], segment: Path.Segment): Plan.Error =
    plan match {
      case suppressed: Plan.Error =>
        Plan.Error.from(plan, ErrorMessage.InvalidPathSegment(segment, config.side, config.span), Some(suppressed))
      case other =>
        Plan.Error.from(plan, ErrorMessage.InvalidPathSegment(segment, config.side, config.span), None)
    }

  private def staticOrDynamic(
    instruction: Configuration.Instruction.Static | Configuration.Instruction.Dynamic,
    current: Plan[Plan.Error],
    parent: Plan[Plan.Error] | None.type
  )(using Quotes, Accumulator[Plan.Error], Accumulator[(Path, Side)]) = {
    instruction match {
      case static: Configuration.Instruction.Static =>
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

  private def regional(
    plan: Plan[Plan.Error],
    modifier: Configuration.Instruction.Regional,
    parent: Plan[Plan.Error] | None.type
  )(using Quotes, Accumulator[Plan.Error], Accumulator[(Path, Side)]): Plan[Plan.Error] =
    plan match {
      case plan: Upcast => plan

      case plan: UserDefined => plan

      case plan: Derived => plan

      case plan: Configured => plan

      case plan: BetweenProductFunction[Plan.Error] =>
        plan.copy(argPlans = plan.argPlans.transform((_, argPlan) => regional(argPlan, modifier, plan)))

      case plan: BetweenUnwrappedWrapped => plan

      case plan: BetweenWrappedUnwrapped => plan

      case plan: BetweenSingletons => plan

      case plan: BetweenProducts[Plan.Error] =>
        plan.copy(fieldPlans = plan.fieldPlans.transform((_, fieldPlan) => regional(fieldPlan, modifier, plan)))

      case plan: BetweenCoproducts[Plan.Error] =>
        plan.copy(casePlans = plan.casePlans.map(regional(_, modifier, plan)))

      case plan: BetweenOptions[Plan.Error] =>
        plan.copy(plan = regional(plan.plan, modifier, plan))

      case plan: BetweenNonOptionOption[Plan.Error] =>
        plan.copy(plan = regional(plan.plan, modifier, plan))

      case plan: BetweenCollections[Plan.Error] =>
        plan.copy(plan = regional(plan.plan, modifier, plan))

      case plan: Error =>
        // TODO: Detect when a regional config doesn't do anything and emit an error
        modifier.modifier(parent, plan) match {
          case config: Configuration => plan.configureIfValid(modifier, config)
          case other: plan.type      => other
        }
    }

  private def bulk(
    current: Plan[Plan.Error],
    instruction: Configuration.Instruction.Bulk
  )(using Quotes, Accumulator[Plan.Error], Accumulator[(Path, Side)]): Plan[Error] = {

    enum IsAnythingModified {
      case Yes, No
    }

    var isAnythingModified = IsAnythingModified.No

    def updatePlan(
      parent: Plan.BetweenProducts[Plan.Error] | Plan.BetweenProductFunction[Plan.Error]
    )(
      name: String,
      plan: Plan[Plan.Error]
    )(using Quotes) = {
      instruction.modifier(parent, name, plan) match {
        case config: Configuration =>
          isAnythingModified = IsAnythingModified.Yes
          plan.configureIfValid(instruction, config)
        case p: plan.type => p
      }
    }

    current
      .narrow[Plan.BetweenProductFunction[Plan.Error] | Plan.BetweenProducts[Plan.Error]]
      .toRight("This config only works when sideing a function-to-product or a product-to-product transformations")
      .map {
        case func: Plan.BetweenProductFunction[Plan.Error] =>
          val updatedArgPlans = func.argPlans.transform(updatePlan(func))
          func.copy(argPlans = updatedArgPlans)
        case prod: Plan.BetweenProducts[Plan.Error] =>
          val updatedFieldPlans = prod.fieldPlans.transform(updatePlan(prod))
          prod.copy(fieldPlans = updatedFieldPlans)
      }
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

  extension (currentPlan: Plan[Plan.Error]) {

    private def configureIfValid(
      instruction: Configuration.Instruction,
      config: Configuration
    )(using quotes: Quotes, errors: Accumulator[Plan.Error], successes: Accumulator[(Path, Side)]) = {
      def isReplaceableBy(update: Configuration)(using Quotes) =
        update.tpe.repr <:< currentPlan.destPath.currentTpe.repr

      if isReplaceableBy(config) then
        Accumulator.append {
          if instruction.side == Side.Dest then currentPlan.destPath -> instruction.side
          else currentPlan.sourcePath -> instruction.side
        }
        Plan.Configured.from(currentPlan, config)
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

}
