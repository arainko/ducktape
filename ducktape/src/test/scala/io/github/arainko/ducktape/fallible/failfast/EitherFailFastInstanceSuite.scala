package io.github.arainko.ducktape.fallible.failfast

import io.github.arainko.ducktape.fallible.model.FailFastFailure
import io.github.arainko.ducktape.Transformer

class EitherFailFastInstanceSuite extends NonDerivedInstanceSuite[FailFastFailure](
  isFailed = [A] => (fa: Either[String, A]) => fa.isLeft,
  deriveError = int => Left(int.toString),
  F = Transformer.Mode.FailFast.either[String]
)
