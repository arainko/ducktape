package io.github.arainko.ducktape.issues

import io.github.arainko.ducktape.*

// https://github.com/arainko/ducktape/issues/37
class Issue37Spec extends DucktapeSuite {
  final case class Rec[A](value: A, rec: Option[Rec[A]])

  given rec[A, B](using Transformer[A, B]): Transformer[Rec[A], Rec[B]] = Transformer.define[Rec[A], Rec[B]].build()

  test("value class transformers don't interfere with primitives") {
    val actual = rec[Int, Option[Int]].transform(Rec(1, Some(Rec(2, None))))
    val expected: Rec[Option[Int]] = Rec(Some(1), Some(Rec(Some(2), None)))

    assertEquals(actual, expected)
  }

}
