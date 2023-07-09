package io.github.arainko.ducktape

import scala.quoted.*
import io.github.arainko.ducktape.internal.modules.*

opaque type Path = Vector[Path.Segment]

object Path {
  val empty: Path = Vector.empty

  enum Segment {
    case Field(name: String)
    case Case(tpe: Type[?])
  }

  extension (self: Path) {
    infix def / (segment: Path.Segment): Path = self.appended(segment)

    def toVector: Vector[Path.Segment] = self.toVector

    def toList: List[Path.Segment] = self.toList

    def render(using Quotes): String = {
      import quotes.reflect.*
      given Printer[TypeRepr] = Printer.TypeReprShortCode

      self.map {
        case Segment.Field(name) => name
        case Segment.Case(tpe) => s"at[${tpe.repr.show}]"
      }.mkString("_.", ".", "")
    }
  }
}
