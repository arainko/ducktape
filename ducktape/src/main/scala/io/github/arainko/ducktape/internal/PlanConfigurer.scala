package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.internal.Configuration.Instruction

import scala.collection.mutable.Builder
import scala.quoted.*
import io.github.arainko.ducktape.internal.Configuration.Traversal

private[ducktape] object PlanConfigurer {
  import Plan.*

  def run(plan: Plan[Plan.Error], configs: List[Configuration.Instruction])(using Quotes): Plan.Reconfigured = {
    def configureSingle(
      plan: Plan[Plan.Error],
      config: Configuration.Instruction
    )(using Quotes, Accumulator[Plan.Error], Accumulator[(Path, Target)]): Plan[Plan.Error] = {
      val trace = Vector.newBuilder[Plan[Plan.Error]]

      def recurse(
        current: Plan[Plan.Error],
        segments: List[Path.Segment]
      )(using Quotes): Plan[Plan.Error] = {
        trace.addOne(current)

        segments match {
          case (segment @ Path.Segment.Field(_, fieldName)) :: tail =>
            current match {
              case plan @ BetweenProducts(sourceTpe, destTpe, sourceContext, destContext, fieldPlans) =>
                val fieldPlan =
                  fieldPlans
                    .get(fieldName)
                    .map(fieldPlan => recurse(fieldPlan, tail))
                    .getOrElse(Plan.Error.from(plan, ErrorMessage.InvalidFieldAccessor(fieldName, config.span), None))

                plan.copy(fieldPlans = fieldPlans.updated(fieldName, fieldPlan))

              case plan @ BetweenProductFunction(sourceTpe, destTpe, sourceContext, destContext, argPlans) =>
                val argPlan =
                  argPlans
                    .get(fieldName)
                    .map(argPlan => recurse(argPlan, tail))
                    .getOrElse(Plan.Error.from(plan, ErrorMessage.InvalidArgAccessor(fieldName, config.span), None))

                plan.copy(argPlans = argPlans.updated(fieldName, argPlan))

              case other => invalidPathSegment(config, other, segment)
            }

          case (segment @ Path.Segment.Case(tpe)) :: tail =>
            current match {
              // BetweenNonOptionOption keeps the same type as its source so we passthrough it when traversing source nodes
              case plan: BetweenNonOptionOption[Plan.Error] if config.target.isSource =>
                plan.copy(plan = recurse(plan.plan, segments))

              case plan @ BetweenCoproducts(sourceTpe, destTpe, sourceContext, destContext, casePlans) =>
                def targetTpe(plan: Plan[Plan.Error]) = if (config.target.isSource) plan.source.tpe.repr else plan.dest.tpe.repr

                casePlans.zipWithIndex
                  .find((plan, _) => tpe.repr =:= targetTpe(plan))
                  .map((casePlan, idx) => plan.copy(casePlans = casePlans.updated(idx, recurse(casePlan, tail))))
                  .getOrElse(Plan.Error.from(plan, ErrorMessage.InvalidCaseAccessor(tpe, config.span), None))

              case other => invalidPathSegment(config, other, segment)
            }

          case (segment @ Path.Segment.Element(tpe)) :: tail =>
            current match {
              case p @ BetweenCollections(_, _, _, _, _, plan) =>
                p.copy(plan = recurse(plan, tail))

              case p @ BetweenOptions(_, _, _, _, plan) =>
                p.copy(plan = recurse(plan, tail))

              case p @ BetweenNonOptionOption(_, _, _, _, plan) =>
                p.copy(plan = recurse(plan, tail))

              case other => invalidPathSegment(config, other, segment)
            }

          case Nil =>
            configurePlan(config, current, trace.result())
        }
      }

      recurse(plan, config.path.segments.toList)
    }

    val (errors, (successes, reconfiguredPlan)) =
      Accumulator.use[Plan.Error]:
        Accumulator.use[(Path, Target)]:
          configs.foldLeft(plan)(configureSingle)

    Plan.Reconfigured(errors, successes, reconfiguredPlan)
  }

  private def configurePlan(
    config: Configuration.Instruction,
    current: Plan[Error],
    trace: => Vector[Plan[Plan.Error]]
  )(using Quotes, Accumulator[Plan.Error], Accumulator[(Path, Target)]) = {
    config match {
      case cfg: (Configuration.Instruction.Static | Configuration.Instruction.Dynamic) =>
        // dropRight(1) because 'current' is the last element of the trace
        staticOrDynamic(cfg, current, Traversal(trace.dropRight(1), current))

      case instruction: Configuration.Instruction.Bulk =>
        bulk(current, instruction)

      case cfg @ Configuration.Instruction.Regional(path, target, modifier, span) =>
        regional(current, cfg)

      case cfg @ Configuration.Instruction.Failed(path, target, message, span) =>
        Accumulator.append {
          Plan.Error.from(current, ErrorMessage.ConfigurationFailed(cfg), None)
        }
    }
  }

  private def invalidPathSegment(config: Configuration.Instruction, plan: Plan[Error], segment: Path.Segment): Plan.Error =
    plan match {
      case suppressed: Plan.Error =>
        Plan.Error.from(plan, ErrorMessage.InvalidPathSegment(segment, config.target, config.span), Some(suppressed))
      case other =>
        Plan.Error.from(plan, ErrorMessage.InvalidPathSegment(segment, config.target, config.span), None)
    }

  private def staticOrDynamic(
    instruction: Configuration.Instruction.Static | Configuration.Instruction.Dynamic,
    current: Plan[Plan.Error],
    traversal: => Traversal
  )(using Quotes, Accumulator[Plan.Error], Accumulator[(Path, Target)]) = {
    instruction match {
      case static: Configuration.Instruction.Static =>
        current.configureIfValid(static, static.config)

      case dynamic: Configuration.Instruction.Dynamic =>
        dynamic.config(traversal) match {
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
    modifier: Configuration.Instruction.Regional
  )(using Quotes, Accumulator[Plan.Error], Accumulator[(Path, Target)]): Plan[Plan.Error] =
    plan match {
      case plan: Upcast => plan

      case plan: UserDefined => plan

      case plan: Derived => plan

      case plan: Configured => plan

      case plan: BetweenProductFunction[Plan.Error] =>
        plan.copy(argPlans = plan.argPlans.transform((_, plan) => regional(plan, modifier)))

      case plan: BetweenUnwrappedWrapped => plan

      case plan: BetweenWrappedUnwrapped => plan

      case plan: BetweenSingletons => plan

      case plan: BetweenProducts[Plan.Error] =>
        plan.copy(fieldPlans = plan.fieldPlans.transform((_, plan) => regional(plan, modifier)))

      case plan: BetweenCoproducts[Plan.Error] =>
        plan.copy(casePlans = plan.casePlans.map(regional(_, modifier)))

      case plan: BetweenOptions[Plan.Error] =>
        plan.copy(plan = regional(plan.plan, modifier))

      case plan: BetweenNonOptionOption[Plan.Error] =>
        plan.copy(plan = regional(plan.plan, modifier))

      case plan: BetweenCollections[Plan.Error] =>
        plan.copy(plan = regional(plan.plan, modifier))

      case plan: Error =>
        modifier.modifier(plan) match {
          case config: Configuration => plan.configureIfValid(modifier, config)
          case other: plan.type      => other
        }
    }

  private def bulk(
    current: Plan[Plan.Error],
    instruction: Configuration.Instruction.Bulk
  )(using Quotes, Accumulator[Plan.Error], Accumulator[(Path, Target)]): Plan[Error] = {

    def updatePlan(name: String, plan: Plan[Plan.Error])(using Quotes) = {
      instruction.modifier(name, plan) match {
        case config: Configuration => plan.configureIfValid(instruction, config)
        case p: plan.type          => p
      }
    }

    current
      .narrow[Plan.BetweenProductFunction[Plan.Error] | Plan.BetweenProducts[Plan.Error]]
      .map {
        case func: Plan.BetweenProductFunction[Plan.Error] =>
          val updatedArgPlans = func.argPlans.transform(updatePlan)
          func.copy(argPlans = updatedArgPlans)
        case prod: Plan.BetweenProducts[Plan.Error] =>
          val updatedFieldPlans = prod.fieldPlans.transform(updatePlan)
          prod.copy(fieldPlans = updatedFieldPlans)
      }
      .getOrElse {
        val failed =
          Configuration.Instruction.Failed.from(
            instruction,
            "This config only works when targeting a function to product or product to product transformations"
          )
        Accumulator.append {
          Plan.Error.from(current, ErrorMessage.ConfigurationFailed(failed), None)
        }
      }
  }

  extension (currentPlan: Plan[Plan.Error]) {

    private def configureIfValid(
      instruction: Configuration.Instruction,
      config: Configuration
    )(using quotes: Quotes, errors: Accumulator[Plan.Error], successes: Accumulator[(Path, Target)]) = {
      def isReplaceableBy(update: Configuration)(using Quotes) =
        update.tpe.repr <:< currentPlan.destContext.currentTpe.repr

      if isReplaceableBy(config) then
        Accumulator.append(currentPlan.destContext -> instruction.target) //TODO: Revise
        Plan.Configured.from(currentPlan, config)
      else
        Accumulator.append {
          Plan.Error.from(
            currentPlan,
            ErrorMessage.InvalidConfiguration(
              config.tpe,
              currentPlan.destContext.currentTpe,
              instruction.target,
              instruction.span
            ),
            None
          )
        }
    }
  }

}
