package io.github.arainko.ducktape.builder

import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.internal.macros.DebugMacros

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

  test("The last applied field config is the picked one") {
    val expected = TestClassWithAdditionalString(1, "str", "str-computed")

    val actual =
      testClass
        .into[TestClassWithAdditionalString]
        .transform(
          Field.const(_.additionalArg, "FIRST"),
          Field.renamed(_.additionalArg, _.str),
          Field.computed(_.additionalArg, _.str + "-computed")
        )

    assertEquals(actual, expected)
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
