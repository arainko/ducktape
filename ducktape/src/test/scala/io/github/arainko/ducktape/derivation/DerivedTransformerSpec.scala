package io.github.arainko.ducktape.derivation

import io.github.arainko.ducktape.model.*
import io.github.arainko.ducktape.*
import munit.FunSuite

class DerivedTransformerSpec extends FunSuite {
  test("derived product transformer roundtrip") {
    val primitive = PrimitivePerson(
      "Danzig",
      25,
      PrimitiveContactInfo("555 444 333", "Nowhere City, 42"),
      List("cycling"),
      PrimitiveCoolnessFactor.Cool
    )

    val complex = ComplexPerson(
      Name("Danzig"),
      Age(25),
      ComplexContactInfo(PhoneNumber("555 444 333"), Address("Nowhere City, 42")),
      Vector(Hobby("cycling")),
      ComplexCoolnessFactor.Cool
    )

    val transformedToComplex = primitive.to[ComplexPerson]

    assertEquals(complex, transformedToComplex)
    assertEquals(transformedToComplex.to[PrimitivePerson], primitive)
  }

  test("derived enum transformer should map to cases with same name") {
    val expectedFromPrimitiveMapping = Map(
      PrimitiveCoolnessFactor.Cool -> ComplexCoolnessFactor.Cool,
      PrimitiveCoolnessFactor.Uncool -> ComplexCoolnessFactor.Uncool,
      PrimitiveCoolnessFactor.SomewhatCool -> ComplexCoolnessFactor.SomewhatCool
    )

    val expectedFromComplexMapping = expectedFromPrimitiveMapping.map(_.swap)

    PrimitiveCoolnessFactor.values.foreach { value =>
      val transformed = value.to[ComplexCoolnessFactor]
      assertEquals(expectedFromPrimitiveMapping(value), transformed)
    }

    ComplexCoolnessFactor.values.foreach { value =>
      val transformed = value.to[PrimitiveCoolnessFactor]
      assertEquals(expectedFromComplexMapping(value), transformed)
    }
  }
}
