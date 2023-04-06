package io.github.arainko.ducktape.issues

import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.internal.macros.*

// https://github.com/arainko/ducktape/issues/38
class Issue38Spec extends DucktapeSuite {
  final case class TestClass(str: String, int: Int)

  final case class TestClassWithAdditionalList[A](str: String, int: Int, additionalArg: List[A] = Nil)

  final case class TestClassWithMandatoryAdditionalList(str: String, int: Int, additionalArg: List[String])

  test("derive a transformer for case classes with default values if configured") {
    val testClass = TestClass("str", 1)
    val expected = TestClassWithAdditionalList[String]("str", 1, Nil)

    val actual =
      List(
        testClass
          .into[TestClassWithAdditionalList[String]]
          .transform(
            Field.default(_.additionalArg)
          ),
        Transformer
          .define[TestClass, TestClassWithAdditionalList[String]]
          .build(
            Field.default(_.additionalArg)
          )
          .transform(testClass),
        testClass
          .into[TestClassWithAdditionalList[String]]
          .transform(
            Field.default(_.additionalArg)
          ),
        Transformer
          .define[TestClass, TestClassWithAdditionalList[String]]
          .build(
            Field.default(_.additionalArg)
          )
          .transform(testClass)
      )

    actual.foreach(actual => assertEquals(actual, expected))
  }
}
