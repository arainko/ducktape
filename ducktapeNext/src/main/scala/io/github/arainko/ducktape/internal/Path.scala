package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.internal.*

import scala.quoted.*
import scala.reflect.TypeTest

final case class Path(root: Type[?], segments: Vector[Path.Segment]) { self =>
  def appended(segment: Path.Segment): Path = self.copy(segments = segments.appended(segment))

  def prepended(segment: Path.Segment): Path = self.copy(segments = segments.prepended(segment))

  def currentTpe(using Quotes): Type[?] = {
    import quotes.reflect.*

    segments.reverse.collectFirst { case Path.Segment.Field(tpe, name) => tpe }
      .getOrElse(root)
      .repr
      .widen
      .asType
  }

  def toVector: Vector[Path.Segment] = self.segments

  def toList: List[Path.Segment] = self.segments.toList

  def render(using Quotes): String = {
    import quotes.reflect.*
    given Printer[TypeRepr] = Printer.TypeReprShortCode

    val printerRoot = root.repr.show

    if (self.segments.isEmpty) printerRoot
    else
      self.segments.map {
        case Path.Segment.Field(_, name) => name
        case Path.Segment.Case(tpe)      => s"at[${tpe.repr.show}]"
      }.mkString(s"$printerRoot#", ".", "")
  }
}

object Path {
  def empty(root: Type[?]): Path = Path(root, Vector.empty)

  given debug: Debug[Path] with {
    extension (self: Path) def show(using Quotes): String = self.render
  }

  enum Segment {
    def tpe: Type[?]

    final def narrow[A <: Segment](using tt: TypeTest[Segment, A]): Option[A] = tt.unapply(this)

    case Field(tpe: Type[?], name: String)
    case Case(tpe: Type[?])
  }
}
