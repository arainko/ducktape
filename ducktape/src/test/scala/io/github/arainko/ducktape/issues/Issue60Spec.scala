package io.github.arainko.ducktape.issues

import io.github.arainko.ducktape.*

// https://github.com/arainko/ducktape/issues/60
class Issue60Spec extends DucktapeSuite {
  test("coproduct derivation doesn't get in the way of transforming between options") {
    case class Xxx(foo: Int)
    case class Yyy(foo: Int)

    val a: Option[Xxx] = Option(Xxx(111))
    val actual: Option[Yyy] = a.to[Option[Yyy]]

    assertEquals(actual, Some(Yyy(111)))
  }
}
