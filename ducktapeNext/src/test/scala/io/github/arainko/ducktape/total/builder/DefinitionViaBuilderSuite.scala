package io.github.arainko.ducktape.total.builder

import io.github.arainko.*
import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.total.builder.DefinitionViaBuilderSuite.*
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

    val transformer =
      Transformer
        .define[Sum1, Sum2]
        .build(
          Case.computed[Sum1.Leaf2](leaf2 => Sum2.Leaf1(leaf2.int1, leaf2.str2)),
          Case.const[Sum1.Singleton.type](Sum2.Singleton2)
        )

    val expectedMappings =
      Map(
        Sum1.Leaf1(1, "str") -> Sum2.Leaf1(1, "str"),
        Sum1.Leaf2(2, "str2", List(1, 2, 3)) -> Sum2.Leaf1(2, "str2"),
        Sum1.Leaf3(3, "str3", Some(1)) -> Sum2.Leaf3(3, "str3", Some(1)),
        Sum1.Singleton -> Sum2.Singleton2
      )

    expectedMappings.foreach { (sum1, expected) =>
      assertEquals(transformer.transform(sum1), expected)
    }
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
    }("No field 'additionalArg' found in io.github.arainko.ducktape.total.builder.DefinitionViaBuilderSuite.TestClass @ TestClassWithAdditionalString.additionalArg")
  }

}

object DefinitionViaBuilderSuite {
  final case class TestClass(str: String, int: Int)
  final case class TestClassWithAdditionalList(int: Int, str: String, additionalArg: List[String])
  final case class TestClassWithAdditionalString(int: Int, str: String, additionalArg: String)
}
