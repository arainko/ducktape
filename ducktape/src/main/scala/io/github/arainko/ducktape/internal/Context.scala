package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.internal.Context.NamedTuples

import scala.quoted.*

private[ducktape] sealed trait Context {
  type F <: Fallible

  def summoner: Summoner[F]
  def transformationSite: TransformationSite
  def namedTuples: Option[NamedTuples]

  final def reifyPlan[FF <: Fallible](value: Plan[Erroneous, F])(using ev: Plan[Erroneous, F] =:= Plan[Erroneous, FF]) =
    ev(value)

  final def toTotal: Context.Total = Context.Total(transformationSite, namedTuples)
}

private[ducktape] object Context {
  type Of[F0 <: Fallible] = Context { type F = F0 }

  // unappliedNamedTuple is the type lambda [Names, Values] =>> NamedTuple[Names, Values], used to harvest its type symbol later on
  final class NamedTuples private (private val unappliedNamedTuple: Type[?]) {
    def isNamedTuple(tpe: Type[?])(using Quotes) =
      tpe.repr.dealias.typeSymbol == unappliedNamedTuple.repr.typeSymbol
  }

  object NamedTuples {
    def create(using Quotes): Option[NamedTuples] = {
      import quotes.reflect.*
      Symbol
        .requiredModule("scala.NamedTuple")
        .declaredType("NamedTuple")
        .headOption
        .map(sym => NamedTuples(sym.typeRef.asType))
    }
  }

  transparent inline def current(using ctx: Context): ctx.type = ctx

  extension [F <: Fallible](self: Context.Of[F]) {
    inline def locally[A](inline f: Context.Of[F] ?=> A): A = f(using self)
  }

  case class PossiblyFallible[G[+x]](
    wrapperType: WrapperType[G],
    transformationSite: TransformationSite,
    summoner: Summoner.PossiblyFallible[G],
    mode: TransformationMode[G],
    namedTuples: Option[NamedTuples]
  ) extends Context {
    final type F = Fallible
  }

  case class Total(
    transformationSite: TransformationSite,
    namedTuples: Option[NamedTuples]
  ) extends Context {
    final type F = Nothing

    val summoner: Summoner[Nothing] = Summoner.Total
  }
}
