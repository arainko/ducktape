package io.github.arainko.ducktape.issues

import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.DucktapeSuite

case class A(anotherCaseClass: AnotherCaseClass)
case class AnotherCaseClass(name: String)

object A {
  case class B(anotherCaseClass: AnotherCaseClass)
}

class Issue26Spec extends DucktapeSuite {
  import A.*

  val a = A(AnotherCaseClass("test"))
  val b = a.to[A.B]
}
