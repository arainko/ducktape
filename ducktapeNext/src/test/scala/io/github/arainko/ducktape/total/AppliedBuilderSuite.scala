package io.github.arainko.ducktape.total

import io.github.arainko.ducktape.*

import scala.annotation.nowarn

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
    }(
      "No field 'additionalArg' found in io.github.arainko.ducktape.total.AppliedBuilderSuite.TestClass @ TestClassWithAdditionalList.additionalArg",
      "Configuration is not valid since the provided type (java.lang.String) is not a subtype of scala.collection.immutable.List[scala.Predef.String] @ TestClassWithAdditionalList.additionalArg"
    )
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
    }(
      "Configuration is not valid since the provided type (scala.collection.immutable.List[scala.Int]) is not a subtype of scala.collection.immutable.List[scala.Predef.String] @ TestClassWithAdditionalList.additionalArg",
      "No field 'additionalArg' found in io.github.arainko.ducktape.total.AppliedBuilderSuite.TestClass @ TestClassWithAdditionalList.additionalArg"
    )
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
    }(
      "No field 'additionalArg' found in io.github.arainko.ducktape.total.AppliedBuilderSuite.TestClass @ TestClassWithAdditionalString.additionalArg",
      "Configuration is not valid since the provided type (scala.Int) is not a subtype of java.lang.String @ TestClassWithAdditionalString.additionalArg"
    )
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
    @nowarn("msg=unused local definition")
    final case class Source(int: Int, str: String, list: List[String])

    @nowarn("msg=unused local definition")
    final case class FieldSource(int: Long, str: CharSequence, list: Vector[String])

    assertFailsToCompileWith {
      """
      val source = Source(1, "str", List("list-str"))
      val fieldSource = FieldSource(1L, "char-seq", Vector("vector-str"))

      source.into[Source].transform(Field.allMatching(fieldSource))
      """
    }("No matching fields found @ Source")
  }

  test("The last applied field config is the picked one") {
    final case class FieldSource(additionalArg: String)

    val fieldSource = FieldSource("str-sourced")

    val expected = TestClassWithAdditionalString(1, "str", "str-computed")

    // @nowarn("msg=Field 'additionalArg' is configured multiple times")
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

  test("When configs are applied to the same field repeateadly a warning is emitted".ignore) {
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

  test("sums of products can be configured") {
    enum Sum1 {
      case Leaf1(int: Int, str: String)
      case Leaf2(int1: Int, str2: String, list: List[Int])
      case Leaf3(int3: Int, str3: String, opt: Option[Int])
      case Singleton
    }

    enum Sum2 {
      case Leaf1(int: Int, str: String)
      case Leaf3(int3: Int, str3: String, opt: Option[Int])
      case Singleton2
    }

    val expectedMappings =
      Map(
        Sum1.Leaf1(1, "str") -> Sum2.Leaf1(1, "str"),
        Sum1.Leaf2(2, "str2", List(1, 2, 3)) -> Sum2.Leaf1(2, "str2"),
        Sum1.Leaf3(3, "str3", Some(1)) -> Sum2.Leaf3(3, "str3", Some(1)),
        Sum1.Singleton -> Sum2.Singleton2
      )

    expectedMappings.foreach { (sum1, expected) =>
      val actual = sum1
        .into[Sum2]
        .transform(
          Case.computed[Sum1.Leaf2](leaf2 => Sum2.Leaf1(leaf2.int1, leaf2.str2)),
          Case.const[Sum1.Singleton.type](Sum2.Singleton2)
        )
      assertEquals(actual, expected)
    }
  }

  test("sums with type parameters can be confgured") {
    enum Sum1[A] {
      case Leaf1(int: Int, a: A)
    }

    enum Sum2[A] {
      case Leaf2(int: Int, a: Option[A])
    }

    val leaf1 = Sum1.Leaf1(1, 1)

    val expected = Sum2.Leaf2(1, Some("1"))

    val actual = leaf1
      .into[Sum2[String]]
      .transform(
        Case.computed[Sum1.Leaf1[Int]](a => Sum2.Leaf2(a.int, Some(a.a.toString)))
      )

    assertEquals(actual, expected)
  }

  test("When a Case is configured multiple times a warning is emitted".ignore) {

    assertFailsToCompileWith {
      """
      LessCases.Case1
        .into[MoreCases]
        .transform(
          Case.const[LessCases.Case3.type](MoreCases.Case3),
          Case.const[LessCases.Case3.type](MoreCases.Case3)
        )
      """
    }("Case 'io.github.arainko.ducktape.total.builder.AppliedBuilderSuite.LessCases.Case3' is configured multiple times")
  }

  test("derive a transformer for case classes with default values if configured") {
    final case class TestClass(str: String, int: Int)

    final case class TestClassWithAdditionalGenericArg[A](str: String, int: Int, additionalArg: A = "defaultStr")

    val testClass = TestClass("str", 1)
    val expected = TestClassWithAdditionalGenericArg[String]("str", 1, "defaultStr")

    val actual =
      List(
        testClass
          .into[TestClassWithAdditionalGenericArg[String]]
          .transform(Field.default(_.additionalArg)),
        Transformer
          .define[TestClass, TestClassWithAdditionalGenericArg[String]]
          .build(Field.default(_.additionalArg))
          .transform(testClass)
      )

    actual.foreach(actual => assertEquals(actual, expected))
  }

  test("Field.default fails when a field doesn't have a default value") {
    final case class TestClass(str: String, int: Int)

    @nowarn("msg=unused local definition")
    final case class TestClassWithAdditionalGenericArg[A](str: String, int: Int, additionalArg: A = "defaultStr")

    @nowarn("msg=unused local definition")
    val testClass = TestClass("str", 1)

    assertFailsToCompileWith {
      """
      testClass
        .into[TestClassWithAdditionalGenericArg[String]]
        .transform(
          Field.default(_.int)
        )
      """
    }(
      "The field 'int' doesn't have a default value @ TestClassWithAdditionalGenericArg[String].int",
      "No field 'additionalArg' found in TestClass @ TestClassWithAdditionalGenericArg[String].additionalArg"
    )
  }

  test("Field.default fails when the default doesn't match the expected type") {
    final case class TestClass(str: String, int: Int)

    @nowarn("msg=unused local definition")
    final case class TestClassWithAdditionalGenericArg[A](str: String, int: Int, additionalArg: A = "defaultStr")

    @nowarn("msg=unused local definition")
    val testClass = TestClass("str", 1)

    assertFailsToCompileWith {
      """
      testClass
        .into[TestClassWithAdditionalGenericArg[Int]]
        .transform(
          Field.default(_.additionalArg)
        )
      """
    }(
      "No field 'additionalArg' found in TestClass @ TestClassWithAdditionalGenericArg[Int].additionalArg",
      "Configuration is not valid since the provided type (java.lang.String) is not a subtype of scala.Int @ TestClassWithAdditionalGenericArg[Int].additionalArg"
    )
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
