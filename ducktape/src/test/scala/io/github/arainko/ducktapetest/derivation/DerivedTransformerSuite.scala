package io.github.arainko.ducktapetest.derivation

import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.internal.macros.*
import io.github.arainko.ducktapetest.DucktapeSuite
import io.github.arainko.ducktapetest.model.*
import munit.FunSuite

import scala.compiletime.testing.*
import scala.deriving.Mirror
import io.github.arainko.ducktape.Transformer.ForProduct

object DerivedTransformerSuite {
// If these are declared inside their tests the compiler crashes 🤔
  enum MoreCases {
    case Case1
    case Case2
    case Case3
    case Case4
  }

  enum LessCases {
    case Case1
    case Case2
    case Case3
  }

  enum Enum1 {
    case Case1
    case Case2
    case Case3
  }

  enum Enum2 {
    case Case3
    case Case2
    case Case1
  }
}

final case class Basic1(value: Int, value1: Int)

final case class Basic2(value2: Int, value: Int, extra: String)

class DerivedTransformerSuite extends DucktapeSuite {
  import DerivedTransformerSuite.*

  test("derived product transformer roundtrip") {
    val expectedPrimitive = PrimitivePerson(
      "Danzig",
      25,
      PrimitiveContactInfo("555 444 333", "Nowhere City, 42"),
      List("cycling"),
      PrimitiveCoolnessFactor.Cool
    )

    val expectedComplex = ComplexPerson(
      Name("Danzig"),
      Age(25),
      ComplexContactInfo(PhoneNumber("555 444 333"), Address("Nowhere City, 42")),
      Vector(Hobby("cycling")),
      ComplexCoolnessFactor.Cool
    )

    val actualComplex = 
      DebugMacros.code {
        expectedPrimitive.to[ComplexPerson]
      }
      
    val actualPrimitive =
      DebugMacros.code {

        actualComplex.to[PrimitivePerson]
      }

      
    assertEquals(expectedComplex, actualComplex)
    assertEquals(expectedPrimitive, actualPrimitive)
  }

  test("derived enum transformer should map to cases with same name") {
    val expectedFromEnum1Mapping = Map(
      Enum1.Case1 -> Enum2.Case1,
      Enum1.Case2 -> Enum2.Case2,
      Enum1.Case3 -> Enum2.Case3
    )

    val expectedFromEnum2Mapping = expectedFromEnum1Mapping.map(_.swap)

    Enum1.values.foreach { value =>
      val actual = value.to[Enum2]
      assertEquals(expectedFromEnum1Mapping(value), actual)
    }

    Enum2.values.foreach { value =>
      val actual = value.to[Enum1]
      assertEquals(expectedFromEnum2Mapping(value), actual)
    }
  }

  test("derivation succeeds when going from a class with more fields to a class with less fields") {
    final case class MoreFields(field1: Int, field2: Int, field3: Int, field4: Int)
    final case class LessFields(field1: Int, field2: Int, field3: Int)

    val more = MoreFields(1, 2, 3, 4)
    val expected = LessFields(1, 2, 3)
    val actual = more.to[LessFields]

    assertEquals(expected, actual)
  }

  test("derivation succeeds with more complex subderivations inside") {
    final case class Inside(name: String, age: Int)
    final case class Inside2(name: String)
    final case class Person(name: String, age: Int, ins: Inside)
    final case class Person2(name: String, age: Int, ins: Option[Inside2])

    val person = Person("p1", 1, Inside("ins1", 1))
    val expected = Person2("p1", 1, Some(Inside2("ins1")))
    val actual = person.to[Person2]

    assertEquals(actual, expected)
  }

  test("derivation fails when going from a product with less fields to a product with more fields") {
    assertFailsToCompileWith {
      """
      final case class MoreFields(field1: Int, field2: Int, field3: Int, field4: Int)
      final case class LessFields(field1: Int, field2: Int, field3: Int)

      val derived = Transformer[LessFields, MoreFields]
      """
    }("No field named 'field4' found in LessFields")
  }

  test("derivation succeeds when going from a sum with less cases to a sum with more cases") {
    val transformer = Transformer[LessCases, MoreCases]
    val expected = MoreCases.Case3
    val actual = transformer.transform(LessCases.Case3)

    assertEquals(actual, expected)
  }

  test("derivation fails when going from a sum with more cases to a sum with less cases") {
    assertFailsToCompileWith("Transformer[MoreCases, LessCases]")("No child named 'Case4' found in LessCases")
  }
}
