package io.github.arainko.ducktape.fallible.model

import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.fallible.FallibleTransformer

type AccumulatingFailure[+A] = Either[List[String], A]
type FailFastFailure[+A] = Either[String, A]

case class Positive(value: Int)

object Positive {
  given accTransformer: FallibleTransformer[AccumulatingFailure, Int, Positive] =
    int => if (int > 0) Right(Positive(int)) else Left(s"$int" :: Nil)

  given failFastTransformer: FallibleTransformer[FailFastFailure, Int, Positive] =
    int => accTransformer.transform(int).left.map(_.head)

  given failFastOptTransformer: FallibleTransformer[Option, Int, Positive] =
    int => accTransformer.transform(int).toOption
}
