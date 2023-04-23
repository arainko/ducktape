package io.github.arainko.ducktape.fallible.failfast

import io.github.arainko.ducktape.Transformer

class OptionFailFastInstanceSuite extends NonDerivedInstanceSuite[Option](
  isFailed = [A] => (fa: Option[A]) => fa.isEmpty,
  deriveError = _ => None,
  Transformer.FailFast.Support.option
)
