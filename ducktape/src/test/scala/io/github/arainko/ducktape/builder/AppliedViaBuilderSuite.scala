package io.github.arainko.ducktape.builder

import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.builder.AppliedViaBuilder
import io.github.arainko.ducktape.builder.AppliedViaBuilderSuite.*
import io.github.arainko.ducktape.function.FunctionArguments
import io.github.arainko.ducktape.internal.macros.DebugMacros

class AppliedViaBuilderSuite extends DucktapeSuite {
  private val testClass = TestClass("str", 1)

  test("Arg.const properly applies a constant to an argument") {
    def method(str: String, int: Int, additionalArg: List[String]) = TestClassWithAdditionalList(int, str, additionalArg)

    val expected = TestClassWithAdditionalList(1, "str", List("const"))

    val actual =
      testClass
        .intoVia(method)
        .transform(Arg.const(_.additionalArg, List("const")))

    assertEquals(actual, expected)
  }

  test("Arg.computed properly applies a function to an argument") {
    def method(str: String, int: Int, additionalArg: List[String]) = TestClassWithAdditionalList(int, str, additionalArg)

    val expected = TestClassWithAdditionalList(1, "str", List("str"))

    val actual =
      testClass
        .intoVia(method)
        .transform(Arg.computed(_.additionalArg, testClass => List(testClass.str)))

    assertEquals(actual, expected)
  }

  test("Arg.renamed properly uses a different field for that argument") {
    def method(str: String, int: Int, additionalArg: String) = TestClassWithAdditionalString(int, str, additionalArg)

    val expected = TestClassWithAdditionalString(1, "str", "str")

    val actual =
      testClass
        .intoVia(method)
        .transform(Arg.renamed(_.additionalArg, _.str))

    assertEquals(actual, expected)
  }

  test("When an Arg is configured multiple times a warning is emitted") {
    def method(str: String, int: Int, additionalArg: String) = TestClassWithAdditionalString(int, str, additionalArg)

    assertFailsToCompileWith {
      """
      testClass
        .intoVia(method)
        .transform(
          Arg.renamed(_.additionalArg, _.str),
          Arg.renamed(_.additionalArg, _.str)
        )
      """
    }("Arg 'additionalArg' is configured multiple times")
  }

  test("Builder reports a missing argument") {
    assertFailsToCompileWith {
      """
      def method(str: String, int: Int, additionalArg: String) = TestClassWithAdditionalString(int, str, additionalArg)

      val actual =
        testClass
          .intoVia(method)
          .transform()
      """
    }("No field named 'additionalArg' found in TestClass")
  }
}

object AppliedViaBuilderSuite {
  final case class TestClass(str: String, int: Int)
  final case class TestClassWithAdditionalList(int: Int, str: String, additionalArg: List[String])
  final case class TestClassWithAdditionalString(int: Int, str: String, additionalArg: String)
}
