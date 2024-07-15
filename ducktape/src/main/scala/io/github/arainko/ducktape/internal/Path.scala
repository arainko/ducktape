package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.internal.Debug.AST
import io.github.arainko.ducktape.internal.Path.Segment
import io.github.arainko.ducktape.internal.*

import scala.quoted.*
import scala.reflect.TypeTest

private[ducktape] final case class Path(root: Type[?], segments: Vector[Path.Segment]) { self =>
  def appended(segment: Path.Segment): Path = self.copy(segments = segments.appended(segment))

  def prepended(segment: Path.Segment): Path = self.copy(segments = segments.prepended(segment))

  // deliberately use something that requires a total function so that when a new Path.Segment is declared
  // it's not forgotten about
  def currentTpe(using Quotes): Type[?] = {
    segments.reverse.find {
      case Path.Segment.Element(_)         => true
      case Path.Segment.Field(_, _)        => true
      case Path.Segment.TupleElement(_, _) => true
      case Path.Segment.Case(_)            => false
    }
      .fold(root)(_.tpe)
      .repr
      .widen
      .asType
  }

  def toVector: Vector[Path.Segment] = self.segments

  def toList: List[Path.Segment] = self.segments.toList

  def isAncestorOrSiblingOf(that: Path)(using Quotes): Boolean = {
    /*
    import quotes.reflect.*

    if (self.segments.length > that.segments.length) false
    else
      self.root.repr =:= that.root.repr && self.segments.zip(that.segments).forall {
        case Path.Segment.Case(leftTpe) -> Path.Segment.Case(rightTpe) =>
          leftTpe.repr =:= rightTpe.repr
        case Path.Segment.Field(leftTpe, leftName) -> Path.Segment.Field(rightTpe, rightName) =>
          leftName == rightName && leftTpe.repr =:= rightTpe.repr
        case _ => false
      }
     */
    val thatRendered = that.render
    val thisRendered = this.render
    Logger.debug("that rendered", thatRendered)
    Logger.debug("this rendered", thisRendered)
    Logger.loggedDebug("Result")(thatRendered.contains(thisRendered))
  }

  def render(using Quotes): String = {
    import quotes.reflect.*

    val printedRoot = root.repr.widen.show(using Printer.TypeReprShortCode)

    if self.segments.isEmpty then printedRoot
    else
      self.segments.map {
        case Path.Segment.Field(_, name)    => name
        case Segment.TupleElement(_, index) => s"apply($index)"
        case Path.Segment.Element(_)        => "element"
        case Path.Segment.Case(tpe) =>
          val repr = tpe.repr
          val suffix = if repr.isSingleton then ".type" else ""
          s"at[${tpe.repr.show(using Printer.TypeReprAnsiCode)}${suffix}]"
      }.mkString(s"$printedRoot.", ".", "")
  }
}

private[ducktape] object Path {
  def empty(root: Type[?]): Path = Path(root, Vector.empty)

  given debug: Debug[Path] with {
    def astify(self: Path)(using Quotes): AST = Debug.AST.Text(self.render)
  }

  enum Segment derives Debug {
    def tpe: Type[?]

    final def narrow[A <: Segment](using tt: TypeTest[Segment, A]): Option[A] = tt.unapply(this)

    case Field(tpe: Type[?], name: String)
    case TupleElement(tpe: Type[?], index: Int)
    case Case(tpe: Type[?])
    case Element(tpe: Type[?])
  }
}
