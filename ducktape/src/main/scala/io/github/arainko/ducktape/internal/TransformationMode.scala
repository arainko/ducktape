package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.Mode

import scala.quoted.*

private[ducktape] enum TransformationMode[F[+x]] {
  def value: Expr[Mode[F]]
  def contextAware: Option[Expr[Mode.Capability.ContextAware { type Self[+x] = F[x] }]]

  def embedContext[A: Type](path: Path, expr: Expr[F[A]])(using Quotes, Type[F]): Expr[F[A]] =
    contextAware
      .fold(expr)(ctxAware => '{ $ctxAware.embedContext[A](${ path.asTransformationPathExpr }, $expr) })

  case Accumulating(
    value: Expr[Mode.Accumulating[F]],
    contextAware: Option[Expr[Mode.Capability.ContextAware { type Self[+x] = F[x] }]]
  )

  case FailFast(
    value: Expr[Mode.FailFast[F]],
    contextAware: Option[Expr[Mode.Capability.ContextAware { type Self[+x] = F[x] }]]
  )
}

private[ducktape] object TransformationMode {
  def create[F[+x]: Type](expr: Expr[Mode[F]])(using Quotes): Option[TransformationMode[F]] = {
    val contextAware =
      expr match
        case '{ $ctxAware: ContextAware[F] } => Some(ctxAware)
        case _                               => None

    expr match
      case '{ $acc: Mode.Accumulating[F] } => Some(Accumulating(acc, contextAware))
      case '{ $ff: Mode.FailFast[F] }      => Some(FailFast(ff, contextAware))
      case _                               => None
  }

  private type ContextAware[F[+x]] = Mode.Capability.ContextAware { type Self[+x] = F[x] }

}
