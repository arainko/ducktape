package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.Mode

import scala.quoted.*

enum TransformationMode[F[+x]] {
  def value: Expr[Mode[F]]

  case Accumulating(value: Expr[Mode.Accumulating[F]])
  case FailFast(value: Expr[Mode.FailFast[F]])
}

object TransformationMode {
  def create[F[+x]: Type](expr: Expr[Mode[F]])(using Quotes): Option[TransformationMode[F]] =
    expr match
      case '{ $acc: Mode.Accumulating[F] } => Some(Accumulating(acc))
      case '{ $ff: Mode.FailFast[F] }      => Some(FailFast(ff))
      case other                           => None

}
