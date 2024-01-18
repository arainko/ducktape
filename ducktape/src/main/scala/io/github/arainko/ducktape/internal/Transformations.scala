package io.github.arainko.ducktape.internal

import scala.quoted.*
import scala.quoted.runtime.StopMacroExpansion

private[ducktape] object Transformations {

  def createOrReportErrors[F <: Fallible](
    plan: Plan[Plan.Error, F],
    configs: List[Configuration.Instruction[F]]
  )(
    create: Plan[Nothing, F] => Expr[Any]
  )(using Quotes) = {
    import quotes.reflect.*

    val reconfiguredPlan = plan.configureAll(configs)

    Logger.info("Original plan", plan)
    Logger.info("Config", configs)
    Logger.info("Reconfigured plan", reconfiguredPlan)

    reconfiguredPlan.result.refine match {
      case Left(errors) =>
        val ogErrors =
          plan.refine.swap
            .map(_.toList)
            .getOrElse(Nil)
            .filterNot(ogError => // filter out things that were successfully configured to not show these to the user
              ogError.message.side match
                case Side.Source =>
                  reconfiguredPlan.successes
                    .exists((path, side) => side == Side.Source && path.isAncestorOrSiblingOf(ogError.sourcePath))
                case Side.Dest =>
                  reconfiguredPlan.successes.exists((path, side) =>
                    side == Side.Dest && path.isAncestorOrSiblingOf(ogError.destPath)
                  )
            )

        val allErrors = errors ::: reconfiguredPlan.errors ::: ogErrors
        val spanForAccumulatedErrors = Span.minimalAvailable(configs.map(_.span))
        allErrors.groupBy {
          _.message.span match
            case None       => spanForAccumulatedErrors
            case span: Span => span
        }
          .transform((_, errors) => errors.map(_.render).toList.distinct.mkString(System.lineSeparator))
          .foreach { (span, errorMessafe) => report.error(errorMessafe, span.toPosition) }

        throw new StopMacroExpansion
      case Right(totalPlan) => create(totalPlan)
    }
  }

  extension (self: Plan.Error)
    private def render(using Quotes) = {
      def renderSingle(error: Plan.Error)(using Quotes) = {
        val renderedPath =
          error.message.side match
            case Side.Source => error.sourcePath.render
            case Side.Dest   => error.destPath.render

        s"${error.message.render} @ $renderedPath"
      }

      def ident(times: Int) = "  " * times

      val suppressedErrors =
        List
          .unfold(self)(_.suppressed.map(suppressedErr => suppressedErr -> suppressedErr))
          .zipWithIndex
          .map((err, depth) =>
            s"SUPPRESSES: ${renderSingle(err)}".linesWithSeparators.map(line => ident(depth + 1) + line).mkString
          )

      String.join(System.lineSeparator, (renderSingle(self) :: suppressedErrors)*)
    }
}
