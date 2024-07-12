package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.internal.Path.Segment

import scala.quoted.{ Quotes, Type }

private[ducktape] sealed trait ErrorMessage derives Debug {
  def render(using Quotes): String
  def span: Span | None.type
  def side: Side
}

private[ducktape] object ErrorMessage {
  final case class NoFieldFound(fieldName: String, fieldTpe: Type[?], sourceTpe: Type[?]) extends ErrorMessage {
    def render(using Quotes): String = s"No field '$fieldName' found in ${sourceTpe.repr.show}"
    val span = None
    val side = Side.Dest
  }

  final case class NoChildFound(childName: String, destTpe: Type[?]) extends ErrorMessage {
    def render(using Quotes): String = s"No child named '$childName' found in ${destTpe.repr.show}"
    def span = None
    val side = Side.Source
  }

  final case class InvalidTupleAccesor(positionIndex: Int, span: Span) extends ErrorMessage {
    def render(using Quotes): String = s"'_.${positionIndex + 1}' or '.apply($positionIndex)' is not a valid tuple accessor"
    val side = Side.Dest
  }

  final case class InvalidFieldAccessor(fieldName: String, span: Span) extends ErrorMessage {
    def render(using Quotes): String = s"'$fieldName' is not a valid field accessor"
    val side = Side.Dest
  }

  final case class InvalidArgAccessor(fieldName: String, span: Span) extends ErrorMessage {
    def render(using Quotes): String = s"'$fieldName' is not a valid arg accessor"
    val side = Side.Dest
  }

  final case class InvalidCaseAccessor(tpe: Type[?], span: Span) extends ErrorMessage {
    def render(using Quotes): String = s"'at[${tpe.repr.show}]' is not a valid case accessor"
    val side = Side.Source
  }

  final case class InvalidConfiguration(configTpe: Type[?], expectedTpe: Type[?], side: Side, span: Span) extends ErrorMessage {

    def render(using Quotes): String = {
      val renderedConfigTpe = configTpe.repr.show
      val renderedExpectedTpe = expectedTpe.repr.show
      s"Configuration is not valid since the provided type (${renderedConfigTpe}) is not a subtype of ${renderedExpectedTpe}"
    }
  }

  final case class CouldntBuildTransformation(source: Type[?], dest: Type[?]) extends ErrorMessage {
    def render(using Quotes): String = s"Couldn't build a transformation plan between ${source.repr.show} and ${dest.repr.show}"
    def span = None
    val side = Side.Dest
  }

  final case class CouldntCreateTransformationFromFunction(span: Span) extends ErrorMessage {
    def render(using Quotes): String = "Couldn't create a transformation plan from a function"
    val side = Side.Dest
  }

  // This error message is not fully right, it happens when some config is already applied to a given field or on a non product/coproduct
  final case class InvalidPathSegment(segment: Path.Segment, side: Side, span: Span) extends ErrorMessage {
    def render(using Quotes): String =
      segment match {
        case Segment.Field(tpe, name) =>
          s"The path segment '$name' is not valid as it is not a field of a case class or an argument of a function"
        case Segment.Case(tpe) =>
          s"The path segment 'at[${tpe.repr.show}]' is not valid because its parent is not a coproduct or the picked type is not a subtype of that coproduct"
        case Segment.Element(_) =>
          s"The path segment 'element' is not valid"
        case Segment.TupleElement(tpe, index) =>
          s"The path segment '_${index + 1}' or '.apply($index)' is not valid"
      }
  }

  final case class ConfigurationFailed(config: Configuration.Instruction.Failed) extends ErrorMessage {
    export config.{ side, span }
    def render(using Quotes): String = config.message
  }

  case object RecursionSuspected extends ErrorMessage {
    def render(using Quotes): String =
      "Recursive type suspected, consider using Transformer.define or Transformer.defineVia instead"
    val span: Span | None.type = None
    val side: Side = Side.Dest
  }

  final case class SourceConfigDoesntEndWithCaseSegment(span: Span) extends ErrorMessage {
    def render(using Quotes): String =
      "Case config's path should always end with an `.at` segment"
    val side: Side = Side.Source
  }

  case object LoopingTransformerDetected extends ErrorMessage {
    val side: Side = Side.Dest

    def render(using Quotes): String =
      "Detected usage of `_.to[B]`, `_.fallibleTo[B]`, `_.into[B].transform()` or `_.into[B].fallible.transform()` in a given Transformer definition which results in a self-looping Transformer. Please use `Transformer.define[A, B]` or `Transformer.define[A, B].fallible` (for some types A and B) to create Transformer definitions"

    val span: Span | None.type = None
  }

  final case class NoFieldFoundAtIndex(fieldIndex: Int, sourceTpe: Type[?]) extends ErrorMessage {
    def render(using Quotes): String = s"No field at index $fieldIndex found in ${sourceTpe.repr.show}"
    val span = None
    val side = Side.Dest
  }

  final case class FallibleConfigNotPermitted(span: Span, side: Side) extends ErrorMessage {
    def render(using Quotes): String =
      """Fallible configuration is not supported for F-unwrapped transformations with Mode.Accumulating.
    |You can make this work if you supply a deprioritized instance of Mode.FailFast for the same wrapper type.""".stripMargin
  }

}
