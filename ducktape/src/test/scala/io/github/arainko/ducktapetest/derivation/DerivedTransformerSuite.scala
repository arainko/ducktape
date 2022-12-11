package io.github.arainko.ducktapetest.derivation

import io.github.arainko.ducktape.Transformer.ForProduct
import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.internal.macros.*
import io.github.arainko.ducktapetest.DucktapeSuite
import io.github.arainko.ducktapetest.model.*
import munit.FunSuite

import scala.compiletime.testing.*
import scala.deriving.Mirror

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

  final case class Wrapped[A](value: A) extends AnyVal
}

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
      List(
        expectedPrimitive.to[ComplexPerson],
        expectedPrimitive.into[ComplexPerson].transform(),
        expectedPrimitive.via(ComplexPerson.apply),
        expectedPrimitive.intoVia(ComplexPerson.apply).transform(),
        Transformer.define[PrimitivePerson, ComplexPerson].build().transform(expectedPrimitive),
        Transformer.defineVia[PrimitivePerson](ComplexPerson.apply).build().transform(expectedPrimitive)
      )

    val actualPrimitive =
      List(
        expectedComplex.to[PrimitivePerson],
        expectedComplex.into[PrimitivePerson].transform(),
        expectedComplex.via(PrimitivePerson.apply),
        expectedComplex.intoVia(PrimitivePerson.apply).transform(),
        Transformer.define[ComplexPerson, PrimitivePerson].build().transform(expectedComplex),
        Transformer.defineVia[ComplexPerson](PrimitivePerson.apply).build().transform(expectedComplex)
      )

    actualComplex.foreach(actual => assertEquals(expectedComplex, actual))
    actualPrimitive.foreach(actual => assertEquals(expectedPrimitive, actual))
  }

  test("derived product transformers take locally scoped Transformers into consideration") {
    val primitive = PrimitivePerson(
      "Danzig",
      25,
      PrimitiveContactInfo("555 444 333", "Nowhere City, 42"),
      List("cycling"),
      PrimitiveCoolnessFactor.Cool
    )

    val expectedComplex = ComplexPerson(
      Name("Danzig-LOCAL"),
      Age(25),
      ComplexContactInfo(PhoneNumber("555 444 333-LOCAL"), Address("Nowhere City, 42")),
      Vector(Hobby("cycling")),
      ComplexCoolnessFactor.Cool
    )

    given Transformer[String, Name] = str => Name(str + "-LOCAL")
    given Transformer[String, PhoneNumber] = str => PhoneNumber(str + "-LOCAL")

    val actualComplex =
      List(
        primitive.to[ComplexPerson],
        primitive.into[ComplexPerson].transform(),
        primitive.via(ComplexPerson.apply),
        primitive.intoVia(ComplexPerson.apply).transform(),
        Transformer.define[PrimitivePerson, ComplexPerson].build().transform(primitive),
        Transformer.defineVia[PrimitivePerson](ComplexPerson.apply).build().transform(primitive)
      )

    actualComplex.foreach(actual => assertEquals(expectedComplex, actual))
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
    val actual =
      List(
        more.to[LessFields],
        more.into[LessFields].transform(),
        more.via(LessFields.apply),
        more.intoVia(LessFields.apply).transform(),
        Transformer.define[MoreFields, LessFields].build().transform(more),
        Transformer.defineVia[MoreFields](LessFields.apply).build().transform(more)
      )

    actual.foreach(actual => assertEquals(expected, actual))
  }

  test("derivation succeeds with more complex subderivations inside") {
    case class Person(int: Int, str: String, inside: Inside)
    case class Person2(int: Int, str: String, inside: Inside2)

    case class Inside(str: String, int: Int, inside: EvenMoreInside)
    case class Inside2(int: Int, str: String, inside: Option[EvenMoreInside2])

    case class EvenMoreInside(str: String, int: Int)
    case class EvenMoreInside2(str: String, int: Int)

    val person = Person(1, "2", Inside("2", 1, EvenMoreInside("asd", 3)))
    val expected = Person2(1, "2", Inside2(1, "2", Some(EvenMoreInside2("asd", 3))))

    final case class Cos(int: Int)
    final case class Tam(int: Option[Int])

    val cos = Cos(1)  

    val value = DebugMacros.code(cos.to[Tam])

    val actual =
      List(
        person.to[Person2],
        person.into[Person2].transform(),
        person.via(Person2.apply),
        person.intoVia(Person2.apply).transform(),
        Transformer.define[Person, Person2].build().transform(person),
        Transformer.defineVia[Person](Person2.apply).build().transform(person)
      )

    actual.foreach(actual => assertEquals(expected, actual))
  }

  test("derived FromAnyVal & ToAnyVal transformers with type parameters roundrip") {
    val wrappedString = Wrapped("asd")
    val unwrapped = "asd"

    assertEquals(wrappedString.to[String], unwrapped)
    assertEquals(unwrapped.to[Wrapped[String]], wrappedString)
  }

  test("products with AnyVal fields with type params roundrip to their primitives") {
    final case class Person[A](name: Wrapped[String], age: Wrapped[Int], homies: List[Wrapped[String]], wildcard: Wrapped[A])
    final case class UnwrappedPerson[A](name: String, age: Int, homies: List[String], wildcard: A)

    val person = Person(Wrapped("Name"), Wrapped(18), List(Wrapped("Homie1")), Wrapped(5L))
    val unwrapped = UnwrappedPerson("Name", 18, List("Homie1"), 5L)

    val actualUnwrapped =
      List(
        person.to[UnwrappedPerson[Long]],
        person.into[UnwrappedPerson[Long]].transform(),
        person.via(UnwrappedPerson.apply[Long]),
        person.intoVia(UnwrappedPerson.apply[Long]).transform(),
        Transformer.define[Person[Long], UnwrappedPerson[Long]].build().transform(person),
        Transformer.defineVia[Person[Long]](UnwrappedPerson.apply[Long]).build().transform(person)
      )

    val actualPerson =
      List(
        unwrapped.to[Person[Long]],
        unwrapped.into[Person[Long]].transform(),
        unwrapped.via(Person.apply[Long]),
        unwrapped.intoVia(Person.apply[Long]).transform(),
        Transformer.define[UnwrappedPerson[Long], Person[Long]].build().transform(unwrapped),
        Transformer.defineVia[UnwrappedPerson[Long]](Person.apply[Long]).build().transform(unwrapped)
      )

    actualUnwrapped.foreach(actual => assertEquals(actual, unwrapped))
    actualPerson.foreach(actual => assertEquals(actual, person))
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
