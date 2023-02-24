package io.github.arainko.ducktape.issues

import io.github.arainko.ducktape.*

// https://github.com/arainko/ducktape/issues/26
class Issue26Spec extends DucktapeSuite {
  case class A(anotherCaseClass: A.AnotherCaseClass)

  object A {
    case class AnotherCaseClass(name: String)

    // note how AnotherCaseClass is not referred to as A.AnotherCaseClass
    case class B(anotherCaseClass: AnotherCaseClass)
  }
  
  test("derive a correct transformer no matter how you refer to A.AnotherCaseClass inside of `A.B`") {
    val expected = A.B(A.AnotherCaseClass("test"))

    val a = A(A.AnotherCaseClass("test"))
    val actual =
      List(
        a.to[A.B],
        a.into[A.B].transform(),
        a.via(A.B.apply),
        a.intoVia(A.B.apply).transform(),
        Transformer.define[A, A.B].build().transform(a),
        Transformer.defineVia[A](A.B.apply).build().transform(a)
      )

    actual.foreach(actual => assertEquals(actual, expected))
  }

}
