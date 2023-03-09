package io.github.arainko.ducktape.issues

import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.internal.macros.*

// https://github.com/arainko/ducktape/issues/38
class Issue38Spec extends DucktapeSuite {
  final case class TestClass(str: String, int: Int)

  final case class TestClassWithAdditionalList(str: String, int: Int, additionalArg: List[String] = Nil)

  test("derive a transformer for case classes with default values") {
    val testClass = TestClass("str", 1)
    val expected = TestClassWithAdditionalList("str", 1, Nil)

    println(testClass)
    println(defaultParams[TestClassWithAdditionalList])

    val actual =
      List(
        testClass.to[TestClassWithAdditionalList],
        testClass.into[TestClassWithAdditionalList].transform(),
        testClass.via(TestClassWithAdditionalList.apply),
        testClass.intoVia(TestClassWithAdditionalList.apply).transform(),
        Transformer.define[TestClass, TestClassWithAdditionalList].build().transform(testClass),
        Transformer.defineVia[TestClass](TestClassWithAdditionalList.apply).build().transform(testClass)
      )

    actual.foreach(actual => assertEquals(actual, expected))
  }

}
