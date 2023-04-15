package io.github.arainko.ducktape.macros

import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.internal.macros.*
import io.github.arainko.ducktape.macros.MakeTransformerChecker
import io.github.arainko.ducktape.model.*
import munit.*

import scala.quoted.*

class MakeTransformerSpec extends DucktapeSuite {

  // test("should match ForProduct.make") {
  //   MakeTransformerChecker.check(Transformer.forProducts[ComplexPerson, PrimitivePerson])
  // }

  test("should match FromAnyVal.make") {
    MakeTransformerChecker.check(Transformer.fromAnyVal[Hobby, String])
  }

  test("should match ToAnyVal.make") {
    MakeTransformerChecker.check(Transformer.toAnyVal[String, Hobby])
  }

}
