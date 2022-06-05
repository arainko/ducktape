package io.github.arainko.ducktape.internal.macros

import scala.quoted.*

final case class Costam(int: Int, str: String)

@main def runThat = {
  import TypedConfig._

  val res = Test.configs(const(_.int, 1), const(_.str, "a"))


  println(res)
}
