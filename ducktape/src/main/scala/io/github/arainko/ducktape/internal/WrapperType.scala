package io.github.arainko.ducktape.internal

import scala.annotation.unused
import scala.quoted.*

private[ducktape] sealed trait WrapperType {
  def unapply(tpe: Type[?])(using Quotes): Option[(WrapperType, Type[?])]
}

private[ducktape] object WrapperType {

  def unapply(using Quotes, Context)(tpe: Type[?]) =
    Context.current match {
      case ctx: Context.PossiblyFallible[?] => ctx.wrapperType.unapply(tpe)
      case Context.Total(_)                 => None
    }

  case object Absent extends WrapperType {
    override def unapply(tpe: Type[? <: AnyKind])(using Quotes): Option[(WrapperType, Type[?])] = None
  }

  final case class Wrapped[F[+x]](wrapperTpe: Type[F]) extends WrapperType {
    override def unapply(tpe: Type[? <: AnyKind])(using Quotes): Option[(WrapperType, Type[?])] = {
      @unused given Type[F] = wrapperTpe
      tpe match
        case '[F[underlying]] => Some(this -> Type.of[underlying])
        case _                => None

    }
  }
}
