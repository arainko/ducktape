package io.github.arainko.ducktape.macros

import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.model.*
import munit.*

class MakeTransformerSpec extends DucktapeSuite {

  test("should match ForProduct.make") {
    MakeTransformerChecker.check(Transformer.betweenProducts[ComplexPerson, PrimitivePerson])
  }

  test("should match FromAnyVal.make") {
    MakeTransformerChecker.check(Transformer.betweenWrappedUnwrapped[Hobby, String])
  }

  test("should match ToAnyVal.make") {
    MakeTransformerChecker.check(Transformer.betweenUnwrappedWrapped[String, Hobby])
  }

}
