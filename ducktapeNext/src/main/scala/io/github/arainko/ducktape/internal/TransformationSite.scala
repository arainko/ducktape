package io.github.arainko.ducktape.internal

import scala.quoted.FromExpr
import scala.quoted.Expr
import scala.quoted.Quotes

enum TransformationSite {
  case Definition
  case Transformation
}

object TransformationSite {
  given fromExpr: FromExpr[TransformationSite] =
    new {
      def unapply(x: Expr[TransformationSite])(using Quotes): Option[TransformationSite] =
        x match {
          case '{ TransformationSite.Definition }     => Some(TransformationSite.Definition)
          case '{ TransformationSite.Transformation } => Some(TransformationSite.Transformation)
          case _                                      => None
        }
    }
}
