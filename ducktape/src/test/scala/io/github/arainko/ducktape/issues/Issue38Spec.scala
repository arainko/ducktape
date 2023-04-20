package io.github.arainko.ducktape.issues

import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.internal.macros.*

// https://github.com/arainko/ducktape/issues/38
class Issue38Spec extends DucktapeSuite {
  final case class TestClass(str: String, int: Int)

  final case class TestClassWithAdditionalGenericArg[A](str: String, int: Int, additionalArg: A = "defaultStr")

  test("derive a transformer for case classes with default values if configured") {
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
    val testClass = TestClass("str", 1)

    assertFailsToCompileWith {
      """
      testClass
        .into[TestClassWithAdditionalGenericArg[String]]
        .transform(
          Field.default(_.int)
        )
      """
    }("No default value for 'int' found in TestClassWithAdditionalGenericArg[String]")
  }

  test("Field.default fails when the default doesn't match the expected type") {
    val testClass = TestClass("str", 1)

    assertFailsToCompileWith {
      """
      testClass
        .into[TestClassWithAdditionalGenericArg[Int]]
        .transform(
          Field.default(_.additionalArg)
        )
      """
    }("The default value of 'TestClassWithAdditionalGenericArg[Int].additionalArg' is not a subtype of Int")
  }
}
