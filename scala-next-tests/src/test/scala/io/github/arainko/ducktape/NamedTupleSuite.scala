package io.github.arainko.ducktape

import munit.*
import scala.deriving.Mirror
import io.github.arainko.ducktape.internal.CodePrinter

class NamedTupleSuite extends FunSuite {
  test("named tuple to case class works") {
    case class Person(int: Int, str: String)

    val tup: (int: Int, str: String) = (1, "str")
    val actual = tup.to[Person]
    val expected = Person(1, "str")
    assertEquals(actual, expected)
  }

  test("case class to named tuple works") {
    case class Person(int: Int, str: String)
    val expected = (int = 1, str = "str")
    val actual = Person(1, "str").to[(int: Int, str: String)]
    assertEquals(actual, expected)
  }
}
