package io.github.arainko.ducktape.total

import io.github.arainko.ducktape.*

class TupleTransformationSuite extends DucktapeSuite {

  transparent inline def testing[A, B](source: A) =
    (
      source.to[B],
      source.into[B].transform(),
      Transformer.define[A, B].build().transform(source),
      // Transformer.defineVia[A](sourceApply)
    )

  test("tuple-to-tuple works") {
    val source: (Int, Int, List[Int], (Int, Int, Int)) = (1, 1, List(1), (1, 2, 3))

    val actual = source.to[(Int, Option[Int], Vector[Int], (Int, Int))]

    val expected = (1, Some(1), Vector(1), (1, 2))

    val b = testing[(Int, Int, List[Int], (Int, Int, Int)), (Int, Option[Int], Vector[Int], (Int, Int))](source)

    assertEachEquals(
      
    )

    assertEquals(actual, expected)
  }

  test("tuple-to-product works") {
    case class DestToplevel(int: Int, opt: Option[Int], coll: Vector[Int], level1: DestLevel1)
    case class DestLevel1(int1: Int, int2: Int)

    val source = (1, 1, List(1), (1, 2, 3))

    val actual = source.to[DestToplevel]

    val expected = DestToplevel(1, Some(1), Vector(1), DestLevel1(1, 2))

    assertEquals(actual, expected)
  }

  test("product-to-tuple works") {
    ???
  }

  test("tuple-to-function works") {
    ???
  }

  test("") {
    ???
  }
}
