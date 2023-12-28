package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.internal.Configuration.Instruction

import scala.collection.mutable.Builder
import scala.quoted.*
import io.github.arainko.ducktape.internal.Configuration.Traversal

private[ducktape] object PlanConfigurer {
  import Plan.*

  def run(plan: Plan[Plan.Error], configs: List[Configuration.Instruction])(using Quotes): Plan.Reconfigured = {
    // Buffer of all errors that originate from a config
    val errors = List.newBuilder[Plan.Error]
    val successful = List.newBuilder[Configuration.Instruction.Static]

    def configureSingle(plan: Plan[Plan.Error], config: Configuration.Instruction)(using Quotes): Plan[Plan.Error] = {
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
            configurePlan(config, current, trace.result(), errors, successful)
        }
      }

      recurse(plan, config.path.segments.toList)
    }

    val reconfiguredPlan = configs.foldLeft(plan)(configureSingle)
    Plan.Reconfigured(errors.result(), successful.result(), reconfiguredPlan)
  }

  private def configurePlan(
    config: Configuration.Instruction,
    current: Plan[Error],
    trace: => Vector[Plan[Plan.Error]],
    errors: Builder[Plan.Error, List[Plan.Error]],
    successful: Builder[Configuration.Instruction.Static, List[Configuration.Instruction.Static]]
  )(using Quotes) = {
    import scala.util.chaining.*

    config match {
      case cfg: (Configuration.Instruction.Static | Configuration.Instruction.Dynamic) =>
        // dropRight(1) because 'current' is the last element of the trace
        staticOrDynamic(cfg, current, Traversal(trace.dropRight(1), current), errors, successful)

      case instruction: Configuration.Instruction.ContextualProduct =>
        current
          .narrow[Plan.BetweenProductFunction[Plan.Error] | Plan.BetweenProducts[Plan.Error]]
          .map {
            case func: Plan.BetweenProductFunction[Plan.Error] =>
              val updatedArgPlans =
                func.argPlans.transform { (name, plan) =>
                  instruction.modifier(name, plan) match {
                    case config: Configuration =>
                      if plan.isReplaceableBy(config) then Plan.Configured.from(plan, config)
                      else
                        Plan.Error
                          .from(
                            current,
                            ErrorMessage
                              .InvalidConfiguration(
                                config.tpe,
                                current.destContext.currentTpe,
                                instruction.target,
                                instruction.span
                              ),
                            None
                          )
                    case p: plan.type => p
                  }
                }
              func.copy(argPlans = updatedArgPlans)
            case prod: Plan.BetweenProducts[Plan.Error] =>
              val updatedFieldPlans =
                prod.fieldPlans.transform { (name, plan) =>
                  instruction.modifier(name, plan) match {
                    case config: Configuration =>
                      if plan.isReplaceableBy(config) then Plan.Configured.from(plan, config)
                      else
                        Plan.Error
                          .from(
                            current,
                            ErrorMessage
                              .InvalidConfiguration(
                                config.tpe,
                                current.destContext.currentTpe,
                                instruction.target,
                                instruction.span
                              ),
                            None
                          )
                    case p: plan.type => p
                  }
                }
              prod.copy(fieldPlans = updatedFieldPlans)
          }
          .getOrElse {
            val failed = new Configuration.Instruction.Failed(
              instruction.path,
              instruction.target,
              "This config only works when targeting a function to product or product to product transformations",
              instruction.span
            )
            Plan.Error.from(current, ErrorMessage.ConfigurationFailed(failed), None).tap(errors.addOne)
          }

      case cfg @ Configuration.Instruction.Regional(path, target, modifier, span) =>
        regional(current, modifier)

      case cfg @ Configuration.Instruction.Failed(path, target, message, span) =>
        Plan.Error.from(current, ErrorMessage.ConfigurationFailed(cfg), None).tap(errors.addOne)
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
    traversal: => Traversal,
    errors: Builder[Plan.Error, List[Plan.Error]],
    successful: Builder[Configuration.Instruction.Static, List[Configuration.Instruction.Static]]
  )(using Quotes) = {
    import scala.util.chaining.*

    instruction match {
      case static: Configuration.Instruction.Static =>
        if (current.isReplaceableBy(static.config)) {
          successful.addOne(static)
          Plan
            .Configured(current.source, current.dest, current.sourceContext, current.destContext, static.config)
        } else
          Plan.Error
            .from(
              current,
              ErrorMessage
                .InvalidConfiguration(static.config.tpe, current.destContext.currentTpe, instruction.target, instruction.span),
              None
            )
            .tap(errors.addOne)

      case dynamic: Configuration.Instruction.Dynamic =>
        dynamic.config(traversal) match {
          case Right(config) =>
            if (current.isReplaceableBy(config)) {
              Plan
                .Configured(current.source, current.dest, current.sourceContext, current.destContext, config)
            } else
              Plan.Error
                .from(
                  current,
                  ErrorMessage
                    .InvalidConfiguration(
                      config.tpe,
                      current.destContext.currentTpe,
                      instruction.target,
                      instruction.span
                    ),
                  None
                )
                .tap(errors.addOne)

          case Left(errorMessage) =>
            val failed = new Configuration.Instruction.Failed(dynamic.path, dynamic.target, errorMessage, dynamic.span)
            Plan.Error.from(current, ErrorMessage.ConfigurationFailed(failed), None).tap(errors.addOne)
        }

    }

  }

  private def regional(
    plan: Plan[Plan.Error],
    modifier: Configuration.Modifier
  )(using Quotes): Plan[Plan.Error] =
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
        modifier(plan) match {
          case config: Configuration =>
            Plan.Configured(plan.source, plan.dest, plan.sourceContext, plan.destContext, config)
          case other: plan.type =>
            other
        }
    }

  extension (currentPlan: Plan[?]) {
    private def isReplaceableBy(update: Configuration)(using Quotes) =
      update.tpe.repr <:< currentPlan.destContext.currentTpe.repr
  }

}
