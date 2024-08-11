package io.github.arainko.ducktape.internal

private[ducktape] sealed trait Context {
  type F <: Fallible

  def summoner: Summoner[F]
  def transformationSite: TransformationSite

  final def reifyPlan[FF <: Fallible](value: Plan[Erroneous, F])(using ev: Plan[Erroneous, F] =:= Plan[Erroneous, FF]) =
    ev(value)

  final def toTotal: Context.Total = Context.Total(transformationSite)
}

private[ducktape] object Context {
  type Of[F0 <: Fallible] = Context { type F = F0 }

  transparent inline def current(using ctx: Context): ctx.type = ctx

  extension [F <: Fallible] (self: Context.Of[F]) {
    inline def locally[A](inline f: Context.Of[F] ?=> A): A = f(using self)
  }

  case class PossiblyFallible[G[+x]](
    wrapperType: WrapperType[G],
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
