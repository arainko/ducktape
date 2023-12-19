package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.internal.Configuration.At

import scala.collection.mutable.Builder
import scala.quoted.*

private[ducktape] object PlanConfigurer {
  import Plan.*

  def run(plan: Plan[Plan.Error], configs: List[Configuration.At])(using Quotes): Plan.Reconfigured = {
    // Buffer of all errors that originate from a config
    val errors = List.newBuilder[Plan.Error]
    val successful = List.newBuilder[Configuration.At.Successful]

    def configureSingle(plan: Plan[Plan.Error], config: Configuration.At)(using Quotes): Plan[Plan.Error] = {

      def recurse(
        current: Plan[Plan.Error],
        segments: List[Path.Segment]
      )(using Quotes): Plan[Plan.Error] = {
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

              case plan @ BetweenProductFunction(sourceTpe, destTpe, sourceContext, destContext, argPlans, function) =>
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
                def targetTpe(plan: Plan[Plan.Error]) = if (config.target.isSource) plan.sourceTpe.repr else plan.destTpe.repr

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

          case Nil => configurePlan(config, current, errors, successful)
        }
      }

      recurse(plan, config.path.segments.toList)
    }

    val reconfiguredPlan = configs.foldLeft(plan)(configureSingle)
    Plan.Reconfigured(errors.result(), successful.result(), reconfiguredPlan)
  }

  private def configurePlan(
    config: Configuration.At,
    current: Plan[Error],
    errors: Builder[Plan.Error, List[Plan.Error]],
    successful: Builder[Configuration.At.Successful, List[Configuration.At.Successful]]
  )(using Quotes) = {
    import scala.util.chaining.*

    extension (currentPlan: Plan[?]) {
      def isReplaceableBy(update: Configuration.At.Successful)(using Quotes) =
        update.config.tpe.repr <:< currentPlan.destContext.currentTpe.repr
    }

    config match {
      case cfg @ Configuration.At.Successful(path, target, config, span) =>
        if (current.isReplaceableBy(cfg)) {
          successful.addOne(cfg)
          Plan
            .Configured(current.sourceTpe, current.destTpe, current.sourceContext, current.destContext, config)
        } else
          Plan.Error
            .from(
              current,
              ErrorMessage.InvalidConfiguration(config.tpe, current.destContext.currentTpe, target, span),
              None
            )
            .tap(errors.addOne)

      case cfg @ Configuration.At.Failed(path, target, message, span) =>
        Plan.Error.from(current, ErrorMessage.ConfigurationFailed(cfg), None).tap(errors.addOne)
    }
  }

  private def invalidPathSegment(config: Configuration.At, plan: Plan[Error], segment: Path.Segment): Plan.Error =
    plan match {
      case suppressed: Plan.Error =>
        Plan.Error.from(plan, ErrorMessage.InvalidPathSegment(segment, config.target, config.span), Some(suppressed))
      case other =>
        Plan.Error.from(plan, ErrorMessage.InvalidPathSegment(segment, config.target, config.span), None)
    }
}
