package io.github.arainko.ducktape.fallible.failfast

import io.github.arainko.ducktape.DucktapeSuite
import io.github.arainko.ducktape.Transformer
import io.github.arainko.ducktape.fallible.model.*

abstract class NonDerivedInstanceSuite[F[+x]](
  isFailed: [A] => F[A] => Boolean,
  deriveError: Int => F[Positive],
  F: Transformer.Mode.FailFast[F]
)(using
  transformer: Transformer.Fallible[F, Int, Positive],
) extends DucktapeSuite {

  private given Transformer.Mode.FailFast[F] = F
  
  test("Transformer.Fallible.betweenCollections succeeds when all of the transformations succeed") {
    val actual =
      Transformer.Fallible
        .betweenCollections[F, Int, Positive, List, Vector]
        .transform(List(1, 2, 3))

    val expected = F.pure((1 to 3).toVector.map(Positive.apply))
    assertEquals(actual, expected)
  }

  test("Transformer.Fallible.betweenCollections fails when even a single transformation fails") {
    val actual =
      Transformer.Fallible
        .betweenCollections[F, Int, Positive, List, Vector]
        .transform(List(-2, -1, 0, 1, 2, 3))

    val expected = F.map(deriveError(-2), Vector(_))
    assert(isFailed(actual))
    assertEquals(actual, expected)
  }

  test("Transformer.Fallible.betweenCollections doesn't blow up the stack when the transformation succeeds") {
    val actual = Transformer.Fallible
      .betweenCollections[F, Int, Positive, List, Vector]
      .transform(List.fill(1000000)(1))

    assert(!isFailed(actual))
  }

  test("Transformer.Fallible.betweenCollections doesn't blow up the stack when failed") {
    val actual = Transformer.Fallible
      .betweenCollections[F, Int, Positive, List, Vector]
      .transform(List.fill(1000000)(1).appended(-1))

    assert(isFailed(actual))
  }

  test("Transformer.Fallible.betweenOptions returns None when input is None") {
    val actual = Transformer.Fallible.betweenOptions.transform(None)
    assertEquals(actual, F.pure(None))
  }

  test("Transformer.Fallible.betweenOptions returns Some when input is a Some and the transformation is successful") {
    val actual = Transformer.Fallible.betweenOptions.transform(Some(1))
    assertEquals(actual, F.pure(Some(Positive(1))))
  }

  test("Transformer.Fallible.betweenOptions fails when input is Some and the transformation fails") {
    val actual = Transformer.Fallible.betweenOptions.transform(Some(0))
    assert(isFailed(actual))
    assertEquals(actual, F.map(deriveError(0), Some.apply))
  }

  test("Transformer.Fallible.betweenNonOptionOption returns Some when the transformation is successful") {
    val actual = Transformer.Fallible.betweenNonOptionOption.transform(1)
    assertEquals(actual, F.pure(Some(Positive(1))))
  }

  test("Transformer.Fallible.betweenNonOptionOption fails when the transformation fails") {
    val actual = Transformer.Fallible.betweenNonOptionOption.transform(0)
    assert(isFailed(actual))
    assertEquals(actual, F.map(deriveError(0), Some.apply))
  }
}
