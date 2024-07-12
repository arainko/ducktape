package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.Mode
import io.github.arainko.ducktape.internal.Debug.AST

import scala.quoted.*

private[ducktape] enum TransformationMode[F[+x]] {
  def value: Expr[Mode[F]]

  case Accumulating(value: Expr[Mode.Accumulating[F]], fallback: Option[Expr[Mode.FailFast[F]]])
  case FailFast(value: Expr[Mode.FailFast[F]])
}

private[ducktape] object TransformationMode {
  def create[F[+x]: Type](expr: Expr[Mode[F]])(using Quotes): TransformationMode[F] =
    expr match
      case '{ $acc: Mode.Accumulating[F] } =>
        Accumulating(acc, Expr.summon[Mode.FailFast[F]])
      case '{ $ff: Mode.FailFast[F] } =>
        FailFast(ff)
      case other =>
        quotes.reflect.report.errorAndAbort(
          "Couldn't determine the transformation mode, make sure an instance of either Mode.FailFast[F] or Mode.Accumulating[F] is in implicit scope"
        )

  given Debug[TransformationMode[?]] with {
    def astify(self: TransformationMode[?])(using Quotes): AST =
      self match
        case Accumulating(value, _) => AST.Text("Accumulating")
        case FailFast(value)        => AST.Text("FailFast")

  }
}
