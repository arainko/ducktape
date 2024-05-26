package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.internal.*

import scala.quoted.*
import scala.reflect.TypeTest
import io.github.arainko.ducktape.TransformationPath
import io.github.arainko.ducktape.internal.Path.Segment

private[ducktape] final case class Path(root: Type[?], segments: Vector[Path.Segment]) { self =>
  def appended(segment: Path.Segment): Path = self.copy(segments = segments.appended(segment))

  def prepended(segment: Path.Segment): Path = self.copy(segments = segments.prepended(segment))

  def currentTpe(using Quotes): Type[?] = {

    segments.reverse.collectFirst {
      case Path.Segment.Element(tpe)     => tpe
      case Path.Segment.Field(tpe, name) => tpe
    }
      .getOrElse(root)
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

    if (self.segments.isEmpty) printedRoot
    else
      self.segments.map {
        case Path.Segment.Field(_, name) => name
        case Path.Segment.Element(_)     => "element"
        case Path.Segment.Case(tpe) =>
          val repr = tpe.repr
          val suffix = if (repr.isSingleton) ".type" else ""
          s"at[${tpe.repr.show(using Printer.TypeReprAnsiCode)}${suffix}]"
      }.mkString(s"$printedRoot.", ".", "")
  }

  def asTransformationPathExpr(using Quotes): Expr[TransformationPath] = {
    import quotes.reflect.*

    val segs = segments.map {
      case Segment.Field(tpe, name) => '{ TransformationPath.Segment.field(${ Expr(name) }) }
      case Segment.Case(tpe) =>
        val tpeName = tpe.repr.show(using Printer.TypeReprShortCode)
        '{ TransformationPath.Segment.subtype(${ Expr(tpeName) }) }
      case Segment.Element(tpe) => '{ TransformationPath.Segment.element }
    }

    val runtimeSegments = Expr.ofList(segs)
    '{ TransformationPath.create($runtimeSegments) }
  }
}

private[ducktape] object Path {
  def empty(root: Type[?]): Path = Path(root, Vector.empty)

  given debug: Debug[Path] with {
    extension (self: Path) def show(using Quotes): String = self.render
  }

  enum Segment derives Debug {
    def tpe: Type[?]

    final def narrow[A <: Segment](using tt: TypeTest[Segment, A]): Option[A] = tt.unapply(this)

    case Field(tpe: Type[?], name: String)
    case Case(tpe: Type[?])
    case Element(tpe: Type[?])
  }
}
