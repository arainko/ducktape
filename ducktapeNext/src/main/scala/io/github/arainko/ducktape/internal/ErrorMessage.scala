package io.github.arainko.ducktape.internal

import scala.quoted.Quotes
import scala.quoted.Type
import scala.annotation.constructorOnly
import io.github.arainko.ducktape.internal.Configuration.Target
import io.github.arainko.ducktape.internal.Path.Segment

sealed abstract class ErrorMessage(val relatesTo: ErrorMessage.RelatesTo) derives Debug {
  def render(using Quotes): String
  def span: Span | None.type
}

object ErrorMessage {
  enum RelatesTo derives Debug {
    case Source, Dest
  }

  object RelatesTo {
    def fromTarget(target: Configuration.Target) =
      target match {
        case Configuration.Target.Source => RelatesTo.Source
        case Configuration.Target.Dest   => RelatesTo.Dest
      }

  }

  final case class NoFieldFound(fieldName: String, sourceTpe: Type[?]) extends ErrorMessage(RelatesTo.Dest) {
    def render(using Quotes): String = s"No field '$fieldName' found in ${sourceTpe.repr.show}"
    def span = None
  }

  final case class NoChildFound(childName: String, destTpe: Type[?]) extends ErrorMessage(RelatesTo.Source) {
    def render(using Quotes): String = s"No child named '$childName' found in ${destTpe.repr.show}"
    def span = None
  }

  final case class InvalidFieldAccessor(fieldName: String, span: Span) extends ErrorMessage(RelatesTo.Dest) {
    def render(using Quotes): String = s"'$fieldName' is not a valid field accessor"
  }

  final case class InvalidArgAccessor(fieldName: String, span: Span) extends ErrorMessage(RelatesTo.Dest) {
    def render(using Quotes): String = s"'$fieldName' is not a valid arg accessor"
  }

  final case class InvalidCaseAccessor(tpe: Type[?], span: Span) extends ErrorMessage(RelatesTo.Source) {
    def render(using Quotes): String = s"'at[${tpe.repr.show}]' is not a valid case accessor"
  }

  final case class InvalidConfiguration(configTpe: Type[?], expectedTpe: Type[?], target: Target, span: Span)
      extends ErrorMessage(RelatesTo.fromTarget(target)) {

    def render(using Quotes): String = {
      val renderedConfigTpe = configTpe.repr.show
      val renderedExpectedTpe = expectedTpe.repr.show
      s"Configuration is not valid since the provided type (${renderedConfigTpe}) is not a subtype of ${renderedExpectedTpe}"
    }
  }

  final case class CouldntBuildTransformation(source: Type[?], dest: Type[?]) extends ErrorMessage(RelatesTo.Dest) {
    def render(using Quotes): String = s"Couldn't build a transformation plan between ${source.repr.show} and ${dest.repr.show}"
    def span = None
  }

  final case class CouldntCreateTransformationFromFunction(span: Span) extends ErrorMessage(RelatesTo.Dest) {
    def render(using Quotes): String = "Couldn't create a transformation plan from a function"
  }

  // This error message is not fully right, it happens when some config is already applied to a given field or on a non product/coproduct
  final case class InvalidPathSegment(segment: Path.Segment, target: Target, span: Span)
      extends ErrorMessage(RelatesTo.fromTarget(target)) {
    def render(using Quotes): String =
      segment match {
        case Segment.Field(tpe, name) =>
          s"The path segment '$name' is not valid as it is not a field of a case class or an argument of a function"
        case Segment.Case(tpe) =>
          s"The path segment 'at[${tpe.repr.show}]' is not valid because its parent is not a coproduct or the picked type is not a subtype of that coproduct"
      }
  }

}

// final case class ErrorMessage()
