package io.github.arainko.ducktape.macros

import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.macros.TransformerLambdaChecker
import io.github.arainko.ducktape.model.*

class TransformerLambdaSpec extends DucktapeSuite {
  // test("should match ForProduct") {
  //   TransformerLambdaChecker.check[Transformer.ForProduct.type](Transformer.forProducts[ComplexPerson, PrimitivePerson])
  // }

  test("should match FromAnyVal") {
    TransformerLambdaChecker
      .check[Transformer.FromAnyVal.type](Transformer.fromAnyVal[Hobby, String])
  }

  test("should match ToAnyVal") {
    TransformerLambdaChecker.check[Transformer.ToAnyVal.type](Transformer.toAnyVal[String, Hobby])
  }
}
