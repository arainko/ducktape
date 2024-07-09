package io.github.arainko.ducktape.internal

sealed trait Context {
  type F <: Fallible

  def summoner: Summoner[F]
  def transformationSite: TransformationSite

  final def reify[FF <: Fallible, G[x]](value: G[FF])(using ev: G[FF] =:= G[F]): G[F] = ev(value)

  final def toTotal: Context.Total = Context.Total(transformationSite)
}

object Context {
  type Of[F0 <: Fallible] = Context { type F = F0 }

  inline def current(using ctx: Context): ctx.type = ctx

  case class PossiblyFallible[G[+x]](
    wrapperType: WrapperType.Wrapped[G],
    transformationSite: TransformationSite,
    summoner: Summoner.PossiblyFallible[G],
    mode: TransformationMode[G]
  ) extends Context {
    final type F = Fallible
  }

  case class Total(
    transformationSite: TransformationSite
  ) extends Context {
    final type F = Nothing

    val summoner: Summoner[Nothing] = Summoner.Total
  }
}
