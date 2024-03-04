package io.github.arainko.ducktape.docs

import io.github.arainko.ducktape.*

object That {
  val field = 1
  def method(arg: Int, arg2: String) = 123
  def methodPoly[A](arg: Int, arg2: String, a: A) = 123
  def methodPolyHigher[F[+a]](arg: Int, arg2: String, a: F[Int]) = 123

  extension (self: Int) {
    def that(uhuhu: Int) = uhuhu
  }
}



object Test {
  Docs.members[Field.type]
}
