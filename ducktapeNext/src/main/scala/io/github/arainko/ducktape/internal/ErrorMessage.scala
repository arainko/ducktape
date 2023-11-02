package io.github.arainko.ducktape.internal

import scala.quoted.Quotes
import scala.quoted.Type
import scala.annotation.constructorOnly
import io.github.arainko.ducktape.internal.Configuration.Target

sealed abstract class ErrorMessage(val relatesTo: ErrorMessage.RelatesTo) derives Debug {
  def render(using Quotes): String
  def span: Option[Span]
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

  final case class NoFieldFound(fieldName: String, sourceTpe: Type[?]) extends ErrorMessage(RelatesTo.Source) {
    def render(using Quotes): String = s"No field '$fieldName' found in ${sourceTpe.repr.show}"
    def span: Option[Span] = None
  }

  final case class NoChildFound(childName: String, destTpe: Type[?]) extends ErrorMessage(RelatesTo.Dest) {
    def render(using Quotes): String = s"No child named '$childName' found in ${destTpe.repr.show}"
    def span: Option[Span] = None
  }

  final case class InvalidFieldAccessor(fieldName: String, val span: Option[Span]) extends ErrorMessage(RelatesTo.Dest) {
    def render(using Quotes): String = s"'$fieldName' is not a valid field accessor"
  }

  final case class InvalidArgAccessor(fieldName: String, val span: Option[Span]) extends ErrorMessage(RelatesTo.Dest) {
    def render(using Quotes): String = s"'$fieldName' is not a valid arg accessor"
  }

  final case class InvalidCaseAccessor(tpe: Type[?], val span: Option[Span]) extends ErrorMessage(RelatesTo.Source) {
    def render(using Quotes): String = s"'at[${tpe.repr.show}]' is not a valid case accessor"
  }

  final case class InvalidConfiguration(configTpe: Type[?], expectedTpe: Type[?], target: Target, val span: Option[Span])
      extends ErrorMessage(RelatesTo.fromTarget(target)) {

    def render(using Quotes): String = {
      val renderedConfigTpe = configTpe.repr.show
      val renderedExpectedTpe = expectedTpe.repr.show
      s"Configuration is not valid since the provided type is ${renderedConfigTpe} but it is not a subtype of ${renderedExpectedTpe}"
    }
  }

  final case class CouldntBuildTransformation(source: Type[?], dest: Type[?]) extends ErrorMessage(RelatesTo.Dest) {
    def render(using Quotes): String = s"Couldn't build a transformation plan between ${source.repr.show} and ${dest.repr.show}"
    def span: Option[Span] = None
  }

  final case class CouldntCreateTransformationFromFunction(_span: Span) extends ErrorMessage(RelatesTo.Dest) {
    def render(using Quotes): String = "Couldn't create a transformation plan from a function"
    def span: Option[Span] = Some(_span)
  }

  final case class InvalidPathSegment(segment: Path.Segment, target: Target, _span: Span) extends ErrorMessage(RelatesTo.fromTarget(target)) {
    def render(using Quotes): String = ???
    def span: Option[Span] = Some(_span)
  }

}

// final case class ErrorMessage()
