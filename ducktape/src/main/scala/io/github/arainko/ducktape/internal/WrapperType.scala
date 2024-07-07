package io.github.arainko.ducktape.internal

import scala.quoted.*
import scala.annotation.unused

sealed trait WrapperType {
  def unapply(tpe: Type[?])(using Quotes): Option[(WrapperType, Type[?])]
}

object WrapperType {

  def unapply(using Quotes, Context[Fallible])(tpe: Type[?]) =
    PartialFunction.condOpt(Context.current) {
      case ctx: Context.PossiblyFallible[?] => ctx.wrapperType.unapply(tpe)
    }.flatten

  case object Absent extends WrapperType {
    override def unapply(tpe: Type[? <: AnyKind])(using Quotes): Option[(WrapperType, Type[?])] = None
  }

  final class Wrapped[F[+x]](val wrapperTpe: Type[F]) extends WrapperType {
    override def unapply(tpe: Type[? <: AnyKind])(using Quotes): Option[(WrapperType, Type[?])] = {
      @unused given Type[F] = wrapperTpe
      tpe match
        case '[F[underlying]] => Some(this -> Type.of[underlying])
        case _ => None
      
    }
  } 
}
