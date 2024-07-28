package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.internal.Debug.AST

import scala.annotation.unused
import scala.quoted.*

private[ducktape] sealed trait WrapperType[F[+x]] {
  def wrapper(using Quotes): Type[F]

  def unapply(tpe: Type[?])(using Quotes): Option[(WrapperType[F], Type[?])]
}

private[ducktape] object WrapperType {
  def create[F[+x]: Type](using Quotes): WrapperType[F] = {
    import quotes.reflect.*

    Type.of[F[Any]] match {
      case '[Option[a]] =>
        Optional.asInstanceOf[WrapperType[F]]
      case other =>
        Wrapped(Type.of[F])
    }
  }

  given Debug[WrapperType[?]] with {
    def astify(self: WrapperType[?])(using Quotes): AST =
      import quotes.reflect.*
      self match
        case Optional            => Debug.AST.Text(s"WrapperType[Option]")
        case Wrapped(wrapperTpe) => Debug.AST.Text(s"WrapperType[${wrapperTpe.repr.show(using Printer.TypeReprShortCode)}]")
  }

  def unapply(using Quotes, Context)(tpe: Type[?]) =
    Context.current match {
      case ctx: Context.PossiblyFallible[?] => ctx.wrapperType.unapply(tpe)
      case Context.Total(_)                 => None
    }

  case object Optional extends WrapperType[Option] {

    def wrapper(using Quotes): Type[Option] = Type.of[Option]

    override def unapply(tpe: Type[? <: AnyKind])(using Quotes): Option[(WrapperType[Option], Type[?])] = {
      tpe match {
        case '[Option[underlying]] => Some(this -> Type.of[underlying])
        case _                     => None
      }
    }
  }

  final case class Wrapped[F[+x]] private[WrapperType] (wrapperTpe: Type[F]) extends WrapperType[F] {
    def wrapper(using Quotes): Type[F] = wrapperTpe

    override def unapply(tpe: Type[? <: AnyKind])(using Quotes): Option[(WrapperType[F], Type[?])] = {
      @unused given Type[F] = wrapperTpe
      tpe match
        case '[F[underlying]] => Some(this -> Type.of[underlying])
        case _                => None

    }
  }
}
