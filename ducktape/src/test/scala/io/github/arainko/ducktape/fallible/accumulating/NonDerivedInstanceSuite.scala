package io.github.arainko.ducktape.fallible.accumulating

import io.github.arainko.ducktape.fallible.Mode.Accumulating
import io.github.arainko.ducktape.fallible.model.*
import io.github.arainko.ducktape.{ DucktapeSuite, Transformer }

class NonDerivedInstanceSuite extends DucktapeSuite {

  private given Accumulating[[A] =>> Either[List[Predef.String], A]] =
    Transformer.Mode.Accumulating.either[String, List]

  test("Transformer.Fallible.betweenCollections succeeds when all of the transformations succeed") {
    val actual =
      Transformer.Fallible
        .betweenCollections[AccumulatingFailure, Int, Positive, List, Vector]
        .transform(List(1, 2, 3))

    val expected = Right((1 to 3).toVector.map(Positive.apply))
    assertEquals(actual, expected)
  }

  test("Transformer.Fallible.betweenCollections fails when only sone of the transformations  succeed") {
    val actual =
      Transformer.Fallible
        .betweenCollections[AccumulatingFailure, Int, Positive, List, Vector]
        .transform(List(-2, -1, 0, 1, 2, 3))

    val expected = Left((-2 to 0).toList.map(_.toString))
    assertEquals(actual, expected)
  }

  test("Transformer.Fallible.betweenCollections accumulates errors in the order they occur") {
    val actual =
      Transformer.Fallible
        .betweenCollections[AccumulatingFailure, Int, Positive, List, Vector]
        .transform(List(-2, -1, 0))

    val expected = Left((-2 to 0).toList.map(_.toString))
    assertEquals(actual, expected)
  }

  test("Transformer.Fallible.betweenCollections doesn't blow up the stack") {
    val actual = Transformer.Fallible
      .betweenCollections[AccumulatingFailure, Int, Positive, List, Vector]
      .transform(List.fill(1000000)(-1))

    assert(actual.isLeft)
  }

  test("Transformer.Fallible.betweenOptions returns None when input is None") {
    val actual = Transformer.Fallible.betweenOptions(using Positive.accTransformer, summon).transform(None)
    assertEquals(actual, Right(None))
  }

  test("Transformer.Fallible.betweenOptions returns Some when input is a Some and the transformation is successful") {
    val actual = Transformer.Fallible.betweenOptions(using Positive.accTransformer, summon).transform(Some(1))
    assertEquals(actual, Right(Some(Positive(1))))
  }

  test("Transformer.Fallible.betweenOptions fails when input is Some and the transformation fails") {
    val actual = Transformer.Fallible.betweenOptions(using Positive.accTransformer, summon).transform(Some(0))
    assertEquals(actual, Left("0" :: Nil))
  }

  test("Transformer.Fallible.betweenNonOptionOption returns Some when the transformation is successful") {
    val actual = Transformer.Fallible.betweenNonOptionOption(using Positive.accTransformer, summon).transform(1)
    assertEquals(actual, Right(Some(Positive(1))))
  }

  test("Transformer.Fallible.betweenNonOptionOption fails when the transformation fails") {
    val actual = Transformer.Fallible.betweenNonOptionOption(using Positive.accTransformer, summon).transform(0)
    assertEquals(actual, Left("0" :: Nil))
  }
}
