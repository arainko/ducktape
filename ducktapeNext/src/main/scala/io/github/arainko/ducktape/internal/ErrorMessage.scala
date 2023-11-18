package io.github.arainko.ducktape.internal

import scala.quoted.Quotes
import scala.quoted.Type
import io.github.arainko.ducktape.internal.Path.Segment

sealed trait ErrorMessage derives Debug {
  def render(using Quotes): String
  def span: Span | None.type
  def target: Target
}

object ErrorMessage {
  final case class NoFieldFound(fieldName: String, fieldTpe: Type[?], sourceTpe: Type[?]) extends ErrorMessage {
    def render(using Quotes): String = s"No field '$fieldName' found in ${sourceTpe.repr.show}"
    def span = None
    val target = Target.Dest
  }

  final case class NoChildFound(childName: String, destTpe: Type[?]) extends ErrorMessage {
    def render(using Quotes): String = s"No child named '$childName' found in ${destTpe.repr.show}"
    def span = None
    val target = Target.Source
  }

  final case class InvalidFieldAccessor(fieldName: String, span: Span) extends ErrorMessage {
    def render(using Quotes): String = s"'$fieldName' is not a valid field accessor"
    val target = Target.Dest
  }

  final case class InvalidArgAccessor(fieldName: String, span: Span) extends ErrorMessage {
    def render(using Quotes): String = s"'$fieldName' is not a valid arg accessor"
    val target = Target.Dest
  }

  final case class InvalidCaseAccessor(tpe: Type[?], span: Span) extends ErrorMessage {
    def render(using Quotes): String = s"'at[${tpe.repr.show}]' is not a valid case accessor"
    val target = Target.Source
  }

  final case class InvalidConfiguration(configTpe: Type[?], expectedTpe: Type[?], target: Target, span: Span)
      extends ErrorMessage {

    def render(using Quotes): String = {
      val renderedConfigTpe = configTpe.repr.show
      val renderedExpectedTpe = expectedTpe.repr.show
      s"Configuration is not valid since the provided type (${renderedConfigTpe}) is not a subtype of ${renderedExpectedTpe}"
    }
  }

  final case class CouldntBuildTransformation(source: Type[?], dest: Type[?]) extends ErrorMessage {
    def render(using Quotes): String = s"Couldn't build a transformation plan between ${source.repr.show} and ${dest.repr.show}"
    def span = None
    val target = Target.Dest
  }

  final case class CouldntCreateTransformationFromFunction(span: Span) extends ErrorMessage {
    def render(using Quotes): String = "Couldn't create a transformation plan from a function"
    val target = Target.Dest
  }

  // This error message is not fully right, it happens when some config is already applied to a given field or on a non product/coproduct
  final case class InvalidPathSegment(segment: Path.Segment, target: Target, span: Span) extends ErrorMessage {
    def render(using Quotes): String =
      segment match {
        case Segment.Field(tpe, name) =>
          s"The path segment '$name' is not valid as it is not a field of a case class or an argument of a function"
        case Segment.Case(tpe) =>
          s"The path segment 'at[${tpe.repr.show}]' is not valid because its parent is not a coproduct or the picked type is not a subtype of that coproduct"
      }
  }

  final case class ConfigurationFailed(config: Configuration.At.Failed) extends ErrorMessage {
    export config.{ span, target }
    def render(using Quotes): String = config.message
  }

  case object RecursionSuspected extends ErrorMessage {
    def render(using Quotes): String = "Recursive type suspected, consider using Transformer.define or Transformer.defineVia instead"
    val span: Span | None.type = None
    val target: Target = Target.Dest
  }
}
