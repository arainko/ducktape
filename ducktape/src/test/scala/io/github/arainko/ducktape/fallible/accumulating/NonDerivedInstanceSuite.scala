package io.github.arainko.ducktape.fallible.accumulating

import io.github.arainko.ducktape.fallible.Mode.Accumulating
import io.github.arainko.ducktape.fallible.model.*
import io.github.arainko.ducktape.{ DucktapeSuite, Transformer }

import scala.collection.immutable.{ SortedMap, TreeMap }
class NonDerivedInstanceSuite extends DucktapeSuite {

  private given Accumulating[[A] =>> Either[List[Predef.String], A]] =
    Transformer.Mode.Accumulating.either[String, List]

  test("Transformer.Fallible.betweenCollections succeeds when all of the transformations succeed") {
    val actual =
      Transformer.Fallible
        .derive[AccumulatingFailure, List[Int], Vector[Positive]]
        .transform(List(1, 2, 3))

    val expected = Right((1 to 3).toVector.map(Positive.apply))
    assertEquals(actual, expected)
  }

  test("Transformer.Fallible.betweenCollections fails when only some of the transformations succeed") {
    val actual =
      Transformer.Fallible
        .derive[AccumulatingFailure, List[Int], Vector[Positive]]
        .transform(List(-2, -1, 0, 1, 2, 3))

    val expected = Left((-2 to 0).toList.map(_.toString))
    assertEquals(actual, expected)
  }

  test("Transformer.Fallible.betweenMaps works") {
    val actual =
      Transformer.Fallible
        .derive[AccumulatingFailure, SortedMap[Int, Int], TreeMap[Positive, Positive]]
        .transform(SortedMap(1 -> 1, 2 -> 2, 3 -> 3))

    val expected = TreeMap(Positive(1) -> Positive(1), Positive(2) -> Positive(2), Positive(3) -> Positive(3))
    assertEquals(actual, Right(expected))
  }

  test("Transformer.Fallible.betweenCollections accumulates errors in the order they occur") {
    val actual =
      Transformer.Fallible
        .derive[AccumulatingFailure, List[Int], Vector[Positive]]
        .transform(List(-2, -1, 0))

    val expected = Left((-2 to 0).toList.map(_.toString))
    assertEquals(actual, expected)
  }

  test("Transformer.Fallible.betweenCollections doesn't blow up the stack") {
    val actual = Transformer.Fallible
      .derive[AccumulatingFailure, List[Int], Vector[Positive]]
      .transform(List.fill(1000000)(-1))

    assert(actual.isLeft)
    assertEquals(actual.left.map(_.size), Left(1000000))
  }

  test("Transformer.Fallible.betweenMaps accumulates errors from both keys and values") {
    val actual =
      Transformer.Fallible
        .derive[AccumulatingFailure, SortedMap[Int, Int], TreeMap[Positive, Positive]]
        .transform(SortedMap(-1 -> -1, -2 -> -2, -3 -> -3))

    val expected = Left(List("-3", "-3", "-2", "-2", "-1", "-1"))
    assertEquals(actual, expected)
  }

  test("Transformer.Fallible.betweenMaps doesn't blow up the stack") {
    val actual =
      Transformer.Fallible
        .derive[AccumulatingFailure, SortedMap[Int, Int], TreeMap[Positive, Positive]]
        .transform(SortedMap.from((-1000000 to 0).map(int => int -> int)))

    assertEquals(actual.left.map(_.size), Left(2000002))
  }

  test("Transformer.Fallible.betweenOptions returns None when input is None") {
    val actual = Transformer.Fallible.derive[AccumulatingFailure, Option[Int], Option[Positive]].transform(None)
    assertEquals(actual, Right(None))
  }

  test("Transformer.Fallible.betweenOptions returns Some when input is a Some and the transformation is successful") {
    val actual = Transformer.Fallible.derive[AccumulatingFailure, Option[Int], Option[Positive]].transform(Some(1))
    assertEquals(actual, Right(Some(Positive(1))))
  }

  test("Transformer.Fallible.betweenOptions fails when input is Some and the transformation fails") {
    val actual = Transformer.Fallible.derive[AccumulatingFailure, Option[Int], Option[Positive]].transform(Some(0))
    assertEquals(actual, Left("0" :: Nil))
  }

  test("Transformer.Fallible.betweenNonOptionOption returns Some when the transformation is successful") {
    val actual = Transformer.Fallible.derive[AccumulatingFailure, Int, Option[Positive]].transform(1)
    assertEquals(actual, Right(Some(Positive(1))))
  }

  test("Transformer.Fallible.betweenNonOptionOption fails when the transformation fails") {
    val actual = Transformer.Fallible.derive[AccumulatingFailure, Int, Option[Positive]].transform(0)
    assertEquals(actual, Left("0" :: Nil))
  }
}
