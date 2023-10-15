package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.internal.*

import scala.quoted.*

opaque type Path = Vector[Path.Segment]

object Path {
  val empty: Path = Vector.empty

  given debug: Debug[Path] with {
    extension (self: Path) def show(using Quotes): String = self.render
  }

  enum Segment {
    def tpe: Type[?]

    case Field(tpe: Type[?], name: String)
    case Case(tpe: Type[?])
  }

  extension (self: Path) {
    def appended(segment: Path.Segment): Path = self.appended(segment)

    def prepended(segment: Path.Segment): Path = self.prepended(segment)

    def toVector: Vector[Path.Segment] = self.toVector

    def toList: List[Path.Segment] = self.toList

    def render(using Quotes): String = {
      import quotes.reflect.*
      given Printer[TypeRepr] = Printer.TypeReprCode

      if (self.isEmpty) "_"
      else
        self.map {
          case Segment.Field(_, name) => name
          case Segment.Case(tpe)      => s"at[${tpe.repr.show}]"
        }.mkString("_.", ".", "")
    }
  }
}
