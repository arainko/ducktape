package io.github.arainko.ducktape

import scala.quoted.*
import io.github.arainko.ducktape.internal.modules.*

opaque type Path = Vector[Path.Segment]

object Path {
  val empty: Path = Vector.empty

  enum Segment {
    def tpe: Type[?]

    case Field(tpe: Type[?], name: String)
    case Case(tpe: Type[?])
  }

  opaque type NonEmpty <: Path = Path

  object NonEmpty {
    def fromPath(path: Path): Option[Path.NonEmpty] = Option.when(path.nonEmpty)(path)

    extension (self: Path.NonEmpty) {
      def last = self.toVector.last

      def head = self.toVector.head

      def appended(segment: Path.Segment): Path.NonEmpty = Path.appended(self)(segment)

      def prepended(segment: Path.Segment): Path.NonEmpty = Path.prepended(self)(segment)
    }
  }

  extension (self: Path) {
    def appended(segment: Path.Segment): Path = self.appended(segment)

    def prepended(segment: Path.Segment): Path = self.prepended(segment)

    def toVector: Vector[Path.Segment] = self.toVector

    def toList: List[Path.Segment] = self.toList

    def render(using Quotes): String = {
      import quotes.reflect.*
      given Printer[TypeRepr] = Printer.TypeReprShortCode

      self.map {
        case Segment.Field(_, name) => name
        case Segment.Case(tpe) => s"at[${tpe.repr.show}]"
      }.mkString("_.", ".", "")
    }
  }
}
