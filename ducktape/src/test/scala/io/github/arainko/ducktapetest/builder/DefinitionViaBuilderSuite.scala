package io.github.arainko.ducktapetest.builder

import io.github.arainko.ducktapetest.*
import io.github.arainko.ducktape.*
import io.github.arainko.ducktapetest.builder.DefinitionViaBuilderSuite.*
import munit.*

class DefinitionViaBuilderSuite extends DucktapeSuite {
  private val testClass = TestClass("str", 1)

  test("Arg.const properly applies a constant to an argument") {
    def method(str: String, int: Int, additionalArg: List[String]) = TestClassWithAdditionalList(int, str, additionalArg)

    val expected = TestClassWithAdditionalList(1, "str", List("const"))

    val transformer =
      Transformer
        .defineVia[TestClass](method)
        .build(Arg.const(_.additionalArg, List("const")))

    assertEquals(transformer.transform(testClass), expected)
  }

  test("Arg.computed properly applies a function to an argument") {
    def method(str: String, int: Int, additionalArg: List[String]) = TestClassWithAdditionalList(int, str, additionalArg)

    val expected = TestClassWithAdditionalList(1, "str", List("str"))

    val transformer =
      Transformer
        .defineVia[TestClass](method)
        .build(Arg.computed(_.additionalArg, testClass => List(testClass.str)))

    assertEquals(transformer.transform(testClass), expected)
  }

  test("Arg.renamed properly uses a different field for that argument") {
    def method(str: String, int: Int, additionalArg: String) = TestClassWithAdditionalString(int, str, additionalArg)

    val expected = TestClassWithAdditionalString(1, "str", "str")

    val transformer =
      Transformer
        .defineVia[TestClass](method)
        .build(Arg.renamed(_.additionalArg, _.str))

    assertEquals(transformer.transform(testClass), expected)
  }

  test("Builder reports a missing argument") {
    assertFailsToCompileWith {
      """
      def method(str: String, int: Int, additionalArg: String) = TestClassWithAdditionalString(int, str, additionalArg)

      val transformer =
        Transformer
          .defineVia[TestClass](method)
          .build()
      """
    }("No field named 'additionalArg' found in TestClass")
  }

}

object DefinitionViaBuilderSuite {
  final case class TestClass(str: String, int: Int)
  final case class TestClassWithAdditionalList(int: Int, str: String, additionalArg: List[String])
  final case class TestClassWithAdditionalString(int: Int, str: String, additionalArg: String)
}
