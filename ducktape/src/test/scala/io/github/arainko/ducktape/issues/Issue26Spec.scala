package io.github.arainko.ducktape.issues

import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.DucktapeSuite

case class A(anotherCaseClass: A.AnotherCaseClass)

object A {
  case class AnotherCaseClass(name: String)
  case class B(anotherCaseClass: A.AnotherCaseClass)
}

object SomeOtherObject {
}

class Issue26Spec extends DucktapeSuite {
  // import A.*

  test("repro") {
    val a = A(A.AnotherCaseClass("test"))
    val b = a.to[A.B]
  }

}
