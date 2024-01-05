package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.Transformer
import io.github.arainko.ducktape.internal.Summoner.UserDefined.{ FallibleTransformer, TotalTransformer }

import scala.quoted.{ Expr, Quotes, Type }

sealed trait Summoner[+F <: Fallible] {
  def summonUserDefined[A: Type, B: Type](using Quotes): Option[Summoner.UserDefined[F]]
  def summonDerived[A: Type, B: Type](using Quotes): Option[Summoner.Derived[F]]
}

object Summoner {
  def apply[F <: Fallible](using summoner: Summoner[F]): summoner.type = summoner

  object Total extends Summoner[Nothing] {
    override def summonUserDefined[A: Type, B: Type](using Quotes): Option[UserDefined[Nothing]] =
      Expr.summon[Transformer[A, B]].map(Summoner.UserDefined.TotalTransformer.apply)

    override def summonDerived[A: Type, B: Type](using Quotes): Option[Derived[Nothing]] =
      Expr.summon[Transformer.Derived[A, B]].map(Summoner.Derived.TotalTransformer.apply)

  }

  final class PossiblyFallible[F[+x]: Type] extends Summoner[Fallible] {
    override def summonUserDefined[A: Type, B: Type](using Quotes): Option[UserDefined[Fallible]] =
      Total
        .summonUserDefined[A, B]
        .orElse(Expr.summon[Transformer.Fallible[F, A, B]].map(Summoner.UserDefined.FallibleTransformer.apply))

    override def summonDerived[A: Type, B: Type](using Quotes): Option[Derived[Fallible]] =
      Total
        .summonDerived[A, B]
        .orElse(Expr.summon[Transformer.Fallible.Derived[F, A, B]].map(Summoner.Derived.FallibleTransformer.apply))
  }

  sealed trait UserDefined[+F <: Fallible]

  object UserDefined {
    given debug: Debug[UserDefined[Fallible]] = Debug.derived

    case class TotalTransformer(value: Expr[Transformer[?, ?]]) extends UserDefined[Nothing]
    case class FallibleTransformer(value: Expr[Transformer.Fallible[?, ?, ?]]) extends UserDefined[Fallible]
  }

  sealed trait Derived[+F <: Fallible]

  object Derived {
    given debug: Debug[Derived[Fallible]] = Debug.derived

    case class TotalTransformer(value: Expr[Transformer.Derived[?, ?]]) extends Derived[Nothing]
    case class FallibleTransformer(value: Expr[Transformer.Fallible.Derived[?, ?, ?]]) extends Derived[Fallible]
  }
}
