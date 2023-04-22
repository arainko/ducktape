package io.github.arainko.ducktape.fallible.accumulating

import io.github.arainko.ducktape.DucktapeSuite
import io.github.arainko.ducktape.Transformer

class SupportSuite extends DucktapeSuite {
  private val support = Transformer.Mode.Accumulating.either[String, List]

  test("eitherIterableAccumulatingSupport#product succeeds with two Rights") {
    val actual = support.product(Right(1), Right(2))
    val expected = Right(1 -> 2)
    assertEquals(actual, expected)
  }

  test("eitherIterableAccumulatingSupport#product fails with a Left on the left") {
    val actual = support.product(Left("err" :: Nil), Right(2))
    val expected = Left("err" :: Nil)
    assertEquals(actual, expected)
  }

  test("eitherIterableAccumulatingSupport#product fails with a Left on the right") {
    val actual = support.product(Right(1), Left("err" :: Nil))
    val expected = Left("err" :: Nil)
    assertEquals(actual, expected)
  }

  test("eitherIterableAccumulatingSupport#product accumulates errors") {
    def failed(msg: String) = Left(msg :: Nil)
    val success = Right(1)

    val actual = support.product(support.product(support.product(failed("1"), failed("2")), success), failed("3"))
    val expected = Left("1" :: "2" :: "3" :: Nil)
    assertEquals(actual, expected)
  }
}
