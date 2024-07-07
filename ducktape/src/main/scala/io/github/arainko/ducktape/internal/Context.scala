package io.github.arainko.ducktape.internal

sealed trait Context {
  type F <: Fallible

  def summoner: Summoner[F]
  def transformationSite: TransformationSite
}

object Context {
  type Of[F0 <: Fallible] = Context { type F = F0 }

  def current(using ctx: Context): ctx.type = ctx

  case class PossiblyFallible[G[+x]](
    wrapperType: WrapperType.Wrapped[G],
    transformationSite: TransformationSite,
    summoner: Summoner.PossiblyFallible[G],
    mode: TransformationMode[G]
  ) extends Context {
    type F = Fallible
  }

  case class Total(
    transformationSite: TransformationSite
  ) extends Context {
    type F = Nothing

    val summoner: Summoner[Nothing] = Summoner.Total
  }
}
