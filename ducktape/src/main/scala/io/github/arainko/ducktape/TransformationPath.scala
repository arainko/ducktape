package io.github.arainko.ducktape

import io.github.arainko.ducktape.TransformationPath.Segment.Element
import io.github.arainko.ducktape.TransformationPath.Segment.Field
import io.github.arainko.ducktape.TransformationPath.Segment.Subtype

sealed trait TransformationPath {
  def segments: List[TransformationPath.Segment]

  final def render: String =
    segments.map
}

object TransformationPath {
  private final case class Impl(segments: List[TransformationPath.Segment]) extends TransformationPath

  def create(segments: List[Segment]): TransformationPath = Impl(segments)

  sealed trait Segment {
    final def fold[A](
      caseElement: => A,
      caseField: String => A,
      caseSubtype: String => A
    ): A =
      this match
        case Element       => caseElement
        case Field(name)   => caseField(name)
        case Subtype(name) => caseSubtype(name)

    final def render: String = this match
      case Element       => "element"
      case Field(name)   => name
      case Subtype(name) => name

  }

  object Segment {
    private case object Element extends Segment
    private final case class Field(name: String) extends Segment
    private final case class Subtype(name: String) extends Segment

    def element: Segment = Element
    def field(name: String): Segment = Field(name)
    def subtype(name: String): Segment = Subtype(name)
  }

}
