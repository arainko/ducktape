package io.github.arainko.ducktape.internal

import scala.quoted.*

sealed trait Context[+F <: Fallible] {
  def summoner: Summoner[F]
  def transformationSite: TransformationSite
}

object Context {
  def current(using ctx: Context[?]): ctx.type = ctx

  case class PossiblyFallible[G[+x]](
    wrapperType: WrapperType.Wrapped[G],
    transformationSite: TransformationSite,
    summoner: Summoner.PossiblyFallible[G],
    mode: TransformationMode[G]
  ) extends Context[Fallible] 

  case class Total(
    transformationSite: TransformationSite
  ) extends Context[Nothing] {
    val summoner: Summoner[Nothing] = Summoner.Total
  }
}
