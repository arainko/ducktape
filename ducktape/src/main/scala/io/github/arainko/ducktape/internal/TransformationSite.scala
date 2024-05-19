package io.github.arainko.ducktape.internal

import scala.quoted.*

private[ducktape] enum TransformationSite {
  case Definition
  case Transformation
}

private[ducktape] object TransformationSite {
  def current(using ts: TransformationSite): TransformationSite = ts

  def fromStringExpr(value: Expr["transformation" | "definition"])(using Quotes): TransformationSite = {
    import quotes.reflect.*

    summon[FromExpr["transformation" | "definition"]]
      .unapply(value)
      .map {
        case "transformation" => TransformationSite.Transformation
        case "definition"     => TransformationSite.Definition
      }
      .getOrElse(report.errorAndAbort("Couldn't parse TransformationSite from a literal string", value))
  }
}
