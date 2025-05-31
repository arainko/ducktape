package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.internal.*
import io.github.arainko.ducktape.internal.Flag.Linter.markUsage
import io.github.arainko.ducktape.internal.Flag.{ Effect, Kind, Typed }

import scala.quoted.*
import scala.reflect.TypeTest

private[ducktape] case class PlanFlags(source: SideSpecficFlags, dest: SideSpecficFlags) derives Debug {
  def transition[A](
    sourceStep: Step | Passthrough,
    destStep: Step | Passthrough
  )(using Quotes): PlanFlags = this.copy(source.transition(sourceStep), dest.transition(destStep))

  inline def locally[A](inline f: PlanFlags ?=> A): A = f(using this)
}

private[ducktape] object PlanFlags {
  def current(using f: PlanFlags): f.type = f

  val empty = PlanFlags(SideSpecficFlags.create(Vector.empty), SideSpecficFlags.create(Vector.empty))
}

private[ducktape] case class Flag(effect: Flag.Effect, kind: Flag.Kind, span: Span, priority: Priority) derives Debug

private[ducktape] object Flag {
  given Ordering[Flag] = Ordering.by(_.priority)
  sealed trait Effect derives Debug {
    type In
    type Out

    private[Flag] def use(in: In): Out
  }

  object Effect {
    case class FieldRename(renamer: String => String) extends Effect {
      final type In = String
      final type Out = String

      private[Flag] final def use(in: String): String = renamer(in)
    }
    case class CaseRename(renamer: String => String) extends Effect {
      final type In = String
      final type Out = String

      private[Flag] final def use(in: String): String = renamer(in)
    }
  }

  enum Kind derives Debug {
    final def isLocal: Boolean =
      this match
        case Local           => true
        case Regional        => false
        case TypeSpecific(_) => false

    case Local
    case Regional
    case TypeSpecific(tpe: Type[?])
  }

  final case class Typed[+A <: Effect](effect: A, kind: Flag.Kind, span: Span, priority: Priority) derives Debug {
    def use(input: effect.In)(using linter: Linter): effect.Out = {
      linter.markUsage(this)
      effect.use(input)
    }
  }

  object Typed {
    // todo: add orderings for each effect because Ordering is invariant :((((((
    given ordering[A <: Effect]: Ordering[Typed[A]] = Ordering.by(_.priority)
  }

  opaque type Linter = collection.mutable.Map[Span, Linter.Reason]

  object Linter {
    def create(flags: PlanFlags): Linter = {
      def collectFlagSpans(sideSpecificFlags: SideSpecficFlags) =
        sideSpecificFlags.inScope.map(_.span) ++ sideSpecificFlags.outOfScope.map { (_, flag) => flag.span }

      collection.mutable.Map((collectFlagSpans(flags.dest) ++ collectFlagSpans(flags.source)).map(_ -> Reason.Unused)*)
    }

    extension (self: Linter) {
      def markUsage(flag: Flag.Typed[?]): Unit =
        self -= flag.span

      def markOverrides(flags: SortedVector[Flag.Typed[?]]): Unit =
        flags.foldRight(None: None | Typed[?]) { (curr, previous) =>
          previous match {
            case prev: Typed[?] =>
              self.markAsOverriden(curr, prev)
              curr
            case None => curr
          }
        }

      private def markAsOverriden(flag: Flag.Typed[?], overridenBy: Flag.Typed[?]): Unit =
        self.update(flag.span, Reason.Overridden(overridenBy.span))

      def lintedFlags: Lints = self.toMap
    }

    enum Reason {
      case Unused
      case Overridden(overridder: Span)
    }
  }

  opaque type Lints <: Map[Span, Linter.Reason] = Map[Span, Linter.Reason]

  object Lints {
    val empty: Lints = Map.empty
  }
}

// What to support:
// * 'local' flags - i.e. ones that disappear in the next transition step once they reach their destination
// * 'regional' flags - i.e. ones that stick around all the way down till they reach the leaf transformations
// * 'type-specific' flags - like global, but only apply to a given type (can they also be local?)
//
// Local flags are meant to stick around for all of the children of an enum/sealed trait and all fields of a case class.
// For example, if we were to apply a local FieldRename to an enum like this one:
// enum ExampleEnum {
//    case Case1(int: Int, str: String)
//    case Case2(int: Int, str: String)
// }
// it'd mean we want to rename field in all of the cases as well - to targed a specific case we can narrow down with the path with .at[...]
//

private[ducktape] case object Passthrough
private[ducktape] type Passthrough = Passthrough.type

private[ducktape] enum Step derives Debug { self =>
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

private[ducktape] object Step {
  def fromPathSegment(segment: Path.Segment): Step =
    segment match {
      case Path.Segment.Field(tpe, name)         => Field(name)
      case Path.Segment.TupleElement(tpe, index) => TupleElement(index)
      case Path.Segment.Case(tpe)                => Case(tpe)
      case Path.Segment.Element(tpe)             => Element
    }
}

private[ducktape] case class SideSpecficFlags(
  outOfScope: Vector[(List[Step], Flag)],
  inScope: Vector[Flag]
) derives Debug {
  import scala.util.chaining.*

  def get[B <: Effect](tpe: Type[?])(using tt: TypeTest[Effect, B], quotes: Quotes, linter: Flag.Linter): Option[Typed[B]] = {
    val typedFlags = inScope.collect {
      case Flag(tt(effect), kind @ Flag.Kind.TypeSpecific(flagType), span, prio) if tpe.repr <:< flagType.repr =>
        Flag.Typed(effect, kind, span, prio)
      case Flag(tt(effect), kind @ (Flag.Kind.Local | Flag.Kind.Regional), span, prio) =>
        Flag.Typed(effect, kind, span, prio)
    }

    val sortedFlags = SortedVector.from(typedFlags)
    linter.markOverrides(sortedFlags)
    sortedFlags.lastOption
  }

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
        case (segment: Step, other, flag) =>
          Right(None)
      }
    }

    val isCase = PartialFunction.cond(step) { case Step.Case(_) => true }

    SideSpecficFlags(
      nextOutOfScope.flatten,
      // check for isCase here to be able to apply local flags to children of an enum and bubble down to all the non-case children
      nextInScope ++ inScope.filter(flag => !flag.kind.isLocal || isCase)
    )
  }
}

private[ducktape] object SideSpecficFlags {

  def create(flags: Vector[(List[Step], Flag)]) = {
    val (immediateInScope, outsideOfScope) = flags.partitionMap {
      case (Nil, flag)   => Left(flag)
      case (other, flag) => Right(other -> flag)
    }
    SideSpecficFlags(outsideOfScope, immediateInScope)
  }
}
