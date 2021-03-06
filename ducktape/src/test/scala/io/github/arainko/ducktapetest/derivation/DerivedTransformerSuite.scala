package io.github.arainko.ducktapetest.derivation

import io.github.arainko.ducktape.*
import io.github.arainko.ducktapetest.model.*
import munit.FunSuite

import scala.compiletime.testing.*
import io.github.arainko.ducktape.Builder
import io.github.arainko.ducktape.internal.macros.*
import scala.deriving.Mirror

object DerivedTransformerSuite:
// If these are declared inside their tests the compiler crashes 🤔
  enum MoreCases:
    case Case1
    case Case2
    case Case3
    case Case4

  enum LessCases:
    case Case1
    case Case2
    case Case3

  enum Enum1:
    case Case1
    case Case2
    case Case3

  enum Enum2:
    case Case3
    case Case2
    case Case1

end DerivedTransformerSuite

final case class Basic1(value: Int, value1: Int)
final case class Basic2(value2: Int, value: Int, extra: String)

class DerivedTransformerSuite extends FunSuite {
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

    val actualComplex = expectedPrimitive.to[ComplexPerson]

    val actualPrimitive = actualComplex.to[PrimitivePerson]

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

  test("derived value class transformer roundtrip") {
    final case class Value(value: Int)

    val expectedUnwrapped = Value(5).to[Int]
    val expectedWrapped = 5.to[Value]

    assertEquals(expectedUnwrapped, 5)
    assertEquals(expectedWrapped, Value(5))
  }

  test("derivation succeeds when going from a class with more fields to a class with less fields") {
    final case class MoreFields(field1: Int, field2: Int, field3: Int, field4: Int)
    final case class LessFields(field1: Int, field2: Int, field3: Int)

    val more = MoreFields(1, 2, 3, 4)
    val expected = LessFields(1, 2, 3)
    val actual = more.to[LessFields]

    assertEquals(expected, actual)
  }

  test("derivation fails when going from a product with less fields to a product with more fields") {
    val errors = typeCheckErrors {
      """
      final case class MoreFields(field1: Int, field2: Int, field3: Int, field4: Int)
      final case class LessFields(field1: Int, field2: Int, field3: Int)

      val derived = Transformer[LessFields, MoreFields]
      """
    }.map(_.message).mkString

    assertEquals(errors, "No field named 'field4' found in LessFields")
  }

  test("derivation succeeds when going from a sum with less cases to a sum with more cases") {
    val transformer = Transformer[LessCases, MoreCases]
    val expected = MoreCases.Case3
    val actual = transformer.transform(LessCases.Case3)

    assertEquals(actual, expected)
  }

  test("derivation fails when going from a sum with more cases to a sum with less cases") {
    val errors = typeCheckErrors("Transformer[MoreCases, LessCases]").map(_.message).mkString

    assertEquals(
      errors,
      "No child named 'Case4' in LessCases"
    )
  }
}
