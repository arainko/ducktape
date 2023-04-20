package io.github.arainko.ducktape.fallible.model

import io.github.arainko.ducktape.*

type AccumulatingFailure[+A] = Either[List[String], A]
type FailFastFailure[+A] = Either[String, A]

case class Positive(value: Int)

object Positive {
  given accTransformer: Transformer.Accumulating[AccumulatingFailure, Int, Positive] =
    int => if (int > 0) Right(Positive(int)) else Left(s"$int" :: Nil)

  given failFastTransformer:  Transformer.FailFast[FailFastFailure, Int, Positive] =
    int => accTransformer.transform(int).left.map(_.head)

  given failFastOptTransformer:  Transformer.FailFast[Option, Int, Positive] =
    int => accTransformer.transform(int).toOption
}
