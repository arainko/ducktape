package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.internal.*

import scala.quoted.*

case class PlanFlags(source: SideSpecficFlags, dest: SideSpecficFlags) derives Debug {
  def transition[A](
    sourceStep: Step | Passthrough,
    destStep: Step | Passthrough
  )(using Quotes): PlanFlags = this.copy(source.transition(sourceStep), dest.transition(destStep))

  inline def locally[A](inline f: PlanFlags ?=> A): A = f(using this)
}

object PlanFlags {
  def current(using f: PlanFlags): f.type = f

  val empty = PlanFlags(SideSpecficFlags.create(Vector.empty), SideSpecficFlags.create(Vector.empty))
}

case class Flag(effect: Flag.Effect, kind: Flag.Kind, span: Span, priority: Priority) derives Debug

object Flag {
  enum Effect {
    case Defaults, Nones
  }

  enum Kind derives Debug {
    final def isLocal: Boolean =
      this match
        case Local                       => true
        case Regional                    => false
        case TypeSpecific(tpe, Local)    => true
        case TypeSpecific(tpe, Regional) => false

    case Local
    case Regional
    case TypeSpecific(tpe: Type[?], kind: Local.type | Regional.type)
  }
}

// What to support:
// * 'local' flags - i.e. ones that disappear in the next transition step once they reach their destination
// * 'regional' flags - i.e. ones that stick around all the way down till they reach the leaf transformations
// * 'type-specific' flags - like global, but only apply to a given type (can they also be local?)
//
//

case object Passthrough
type Passthrough = Passthrough.type

enum Step { self =>
  case Element
  case Field(name: String)
  case TupleElement(index: Int)
  case Case(tpe: Type[?])

  final infix def =:=(that: Step)(using Quotes): Boolean =
    (self, that) match {
      case (Element, Element)                             => true
      case (Field(selfName), Field(thatName))             => selfName == thatName
      case (TupleElement(selfIdx), TupleElement(thatIdx)) => selfIdx == thatIdx
      case (Case(selfTpe), Case(thatTpe))                 => selfTpe.repr =:= thatTpe.repr
      case _                                              => false
    }
}

object Step {
  def fromPathSegment(segment: Path.Segment): Step =
    segment match {
      case Path.Segment.Field(tpe, name)         => Field(name)
      case Path.Segment.TupleElement(tpe, index) => TupleElement(index)
      case Path.Segment.Case(tpe)                => Case(tpe)
      case Path.Segment.Element(tpe)             => Element
    }
}

case class SideSpecficFlags(
  outOfScope: Vector[(List[Step], Flag)],
  inScope: Vector[Flag]
) derives Debug {

  def has(effect: Flag.Effect): Boolean = inScope.exists(_.effect == effect)

  def transition(step: Step | Passthrough)(using Quotes): SideSpecficFlags = {
    val (nextInScope, nextOutOfScope) = outOfScope.partitionMap { segmentsAndFlag =>
      (step *: segmentsAndFlag) match {
        case (_, Nil, flag) =>
          Left(flag)
        case (Passthrough, path, flag) =>
          Right(Some(path, flag))
        case (segment: Step, head :: Nil, flag) if head =:= segment =>
          Left(flag)
        case (segment: Step, head :: tail, flag) if head =:= segment =>
          Right(Some((tail, flag)))
        // prune these, it means this won't match next matches either (I thiiiiiiiiiiink?)
        case (segment: Step, other, flag) =>
          Right(None)
      }

    }

    SideSpecficFlags(
      nextOutOfScope.flatten,
      nextInScope ++ inScope.filter(!_.kind.isLocal)
    )
  }
}

object SideSpecficFlags {
  def current(using f: SideSpecficFlags): f.type = f

  def create(flags: Vector[(List[Step], Flag)]) = {
    val (immediateInScope, outsideOfScope) = flags.partitionMap {
      case (Nil, flag)   => Left(flag)
      case (other, flag) => Right(other -> flag)
    }
    SideSpecficFlags(outsideOfScope, immediateInScope)
  }
}
