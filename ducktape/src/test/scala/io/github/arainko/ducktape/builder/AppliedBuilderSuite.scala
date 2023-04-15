package io.github.arainko.ducktape.builder

import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.internal.macros.DebugMacros

import scala.annotation.nowarn
import scala.deriving.Mirror

class AppliedBuilderSuite extends DucktapeSuite {
  import AppliedBuilderSuite.*

  private val testClass = TestClass("str", 1)

  test("Field.const properly applies a constant to a field") {
    val expected = TestClassWithAdditionalList(1, "str", List("const"))

    val actual =
      testClass
        .into[TestClassWithAdditionalList]
        .transform(Field.const(_.additionalArg, List("const")))

    assertEquals(actual, expected)
  }

  test("Field.const fails when the field and constant types do not match") {
    assertFailsToCompileWith {
      """
      testClass
        .into[TestClassWithAdditionalList]
        .transform(Field.const(_.additionalArg, "const"))
      """
    }("Cannot prove that String <:< List[String].")
  }

  test("Field.computed properly applies a function to a field") {
    val expected = TestClassWithAdditionalList(1, "str", List("str"))

    val actual =
      testClass
        .into[TestClassWithAdditionalList]
        .transform(Field.computed(_.additionalArg, testClass => List(testClass.str)))

    assertEquals(actual, expected)
  }

  test("Field.computed fails when the result type of the computed function doesn't match the field") {
    assertFailsToCompileWith {
      """
      testClass
        .into[TestClassWithAdditionalList]
        .transform(Field.computed(_.additionalArg, testClass => List(testClass.int)))
      """
    }("Cannot prove that List[Int] <:< List[String].")
  }

  test("Field.renamed properly uses a different field for that argument") {
    val expected = TestClassWithAdditionalString(1, "str", "str")

    val actual =
      testClass
        .into[TestClassWithAdditionalString]
        .transform(Field.renamed(_.additionalArg, _.str))

    assertEquals(actual, expected)
  }

  test("Field.renamed fails when the types of fields do not match") {
    assertFailsToCompileWith {
      """
      testClass
        .into[TestClassWithAdditionalString]
        .transform(Field.renamed(_.additionalArg, _.int))
      """
    }("Cannot prove that Int <:< String.")
  }

  test("Field.allMatching fills in missing fields") {
    final case class Empty()
    final case class FieldSource(str: String, int: Int)

    val initial = Empty()
    val fieldSource = FieldSource("sourced-str", 1)

    val actual = initial.into[TestClass].transform(Field.allMatching(fieldSource))

    val expected = TestClass("sourced-str", 1)

    assertEquals(actual, expected)
  }

  test("Field.allMatching gets all the matching fields from a field source and overwrites existing ones") {
    final case class FieldSource(str: String, additionalArg: List[String])

    val initial = TestClass("str", 1)
    val fieldSource = FieldSource("sourced-str", List("sourced-list"))

    val actual = initial.into[TestClassWithAdditionalList].transform(Field.allMatching(fieldSource))

    val expected = TestClassWithAdditionalList(1, "sourced-str", List("sourced-list"))

    assertEquals(actual, expected)
  }

  test("Field.allMatching only fills in fields that match by name and by type") {
    final case class FieldSource(str: Int, additionalArg: List[String])

    val initial = TestClass("str", 1)
    val fieldSource = FieldSource(1, List("sourced-list"))

    val actual = initial.into[TestClassWithAdditionalList].transform(Field.allMatching(fieldSource))

    val expected = TestClassWithAdditionalList(1, "str", List("sourced-list"))

    assertEquals(actual, expected)
  }

  test("Field.allMatching works with fields that match by name and are a subtype of the expected type") {
    final case class Source(int: Int, str: String)
    final case class FieldSource(number: Integer, list: List[String])
    final case class Dest(int: Int, str: String, list: Seq[String], number: Number)

    val initial = Source(1, "str")
    val fieldSource = FieldSource(1, List("sourced-list"))

    val actual = initial.into[Dest].transform(Field.allMatching(fieldSource))

    val expected = Dest(1, "str", List("sourced-list"), 1)

    assertEquals(actual, expected)
  }

  test("Field.allMatching reports a compiletime failure when none of the fields match") {
    final case class Source(int: Int, str: String, list: List[String])
    final case class FieldSource(int: Long, str: CharSequence, list: Vector[String])

    assertFailsToCompileWith {
      """
      val source = Source(1, "str", List("list-str"))
      val fieldSource = FieldSource(1L, "char-seq", Vector("vector-str"))

      source.into[Source].transform(Field.allMatching(fieldSource))
      """
    }("None of the fields from FieldSource match any of the fields from Source.")
  }

  test("The last applied field config is the picked one") {
    final case class FieldSource(additionalArg: String)

    val fieldSource = FieldSource("str-sourced")

    val expected = TestClassWithAdditionalString(1, "str", "str-computed")

    @nowarn("msg=Field 'additionalArg' is configured multiple times")
    val actual =
      testClass
        .into[TestClassWithAdditionalString]
        .transform(
          Field.const(_.additionalArg, "FIRST"),
          Field.renamed(_.additionalArg, _.str),
          Field.allMatching(fieldSource),
          Field.computed(_.additionalArg, _.str + "-computed")
        )

    assertEquals(actual, expected)
  }

  test("When configs are applied to the same field repeateadly a warning is emitted") {
    assertFailsToCompileWith {
      """
      testClass
        .into[TestClassWithAdditionalString]
        .transform(
          Field.const(_.additionalArg, "FIRST"),
          Field.renamed(_.additionalArg, _.str),
        )
      """
    }("Field 'additionalArg' is configured multiple times")
  }

  test("Case.const properly applies the constant for that subtype") {
    val expected = LessCases.Case3

    val actual =
      MoreCases.Case4
        .into[LessCases]
        .transform(
          Case.const[MoreCases.Case4.type](LessCases.Case3),
          Case.const[MoreCases.Case5.type](LessCases.Case3)
        )

    assertEquals(actual, expected)
  }

  test("Case.computed applies a function to that given subtype") {

    def actual(value: NotEnumMoreCases) =
      value
        .into[MoreCases]
        .transform(
          Case.computed[NotEnumMoreCases.Case4](case4 => if (case4.value == 1) MoreCases.Case1 else MoreCases.Case4)
        )

    val expectedForValue1 = MoreCases.Case1
    val expectedForOther = MoreCases.Case4

    assertEquals(actual(NotEnumMoreCases.Case4(1)), expectedForValue1)
    assertEquals(actual(NotEnumMoreCases.Case4(2)), expectedForOther)
  }

}

object AppliedBuilderSuite {
  final case class TestClass(str: String, int: Int)
  final case class TestClassWithAdditionalList(int: Int, str: String, additionalArg: List[String])
  final case class TestClassWithAdditionalString(int: Int, str: String, additionalArg: String)

  enum MoreCases {
    case Case1
    case Case2
    case Case3
    case Case4
    case Case5
  }

  enum NotEnumMoreCases {
    case Case1
    case Case2
    case Case3
    case Case4(value: Int)
  }

  enum LessCases {
    case Case1
    case Case2
    case Case3
  }
}
