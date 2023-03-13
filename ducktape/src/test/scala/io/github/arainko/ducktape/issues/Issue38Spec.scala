package io.github.arainko.ducktape.issues

import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.internal.macros.*

// https://github.com/arainko/ducktape/issues/38
class Issue38Spec extends DucktapeSuite {
  final case class TestClass(str: String, int: Int)

  final case class TestClassWithAdditionalList(str: String, int: Int, additionalArg: List[String] = Nil)

  private def method(str: String, int: Int, additionalArg: List[String] = Nil): TestClassWithAdditionalList =
    TestClassWithAdditionalList(str, int, additionalArg)

  test("derive a transformer for case classes with default values if configured") {
    val testClass = TestClass("str", 1)
    val expected = TestClassWithAdditionalList("str", 1, Nil)

    val actual =
      List(
        testClass.to[TestClassWithAdditionalList], // TODO: this should fail without configuration
        testClass
          .into[TestClassWithAdditionalList]
          .transform(
            Field.default(_.additionalArg)
          ),
        testClass
          .intoVia(TestClassWithAdditionalList.apply)
          .transform(
            Arg.default(_.additionalArg)
          ),
        testClass
          .intoVia(method)
          .transform(
            Arg.default(_.additionalArg)
          ),
        Transformer
          .define[TestClass, TestClassWithAdditionalList]
          .build(
            Field.default(_.additionalArg)
          )
          .transform(testClass),
        Transformer
          .defineVia[TestClass](TestClassWithAdditionalList.apply)
          .build(
            Arg.default(_.additionalArg)
          )
          .transform(testClass),
        Transformer
          .defineVia[TestClass](method)
          .build(
            Arg.default(_.additionalArg)
          )
          .transform(testClass)
      )

    actual.foreach(actual => assertEquals(actual, expected))
  }
}
