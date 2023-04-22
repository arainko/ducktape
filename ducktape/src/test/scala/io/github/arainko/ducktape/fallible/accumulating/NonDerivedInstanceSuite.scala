// package io.github.arainko.ducktape.fallible.accumulating

// import io.github.arainko.ducktape.DucktapeSuite
// import io.github.arainko.ducktape.Transformer
// import io.github.arainko.ducktape.fallible.model.*
// import scala.collection.Factory

// class NonDerivedInstanceSuite extends DucktapeSuite {
  

//   test("Transformer.Accumulating.betweenCollections succeeds when all of the transformations succeed") {
//     val actual =
//       Transformer.Accumulating
//         .betweenCollections[AccumulatingFailure, Int, Positive, List, Vector]
//         .transform(List(1, 2, 3))

//     val expected = Right((1 to 3).toVector.map(Positive.apply))
//     assertEquals(actual, expected)
//   }

//   test("Transformer.Accumulating.betweenCollections fails when only sone of the transformations  succeed") {
//     val actual =
//       Transformer.Accumulating
//         .betweenCollections[AccumulatingFailure, Int, Positive, List, Vector]
//         .transform(List(-2, -1, 0, 1, 2, 3))

//     val expected = Left((-2 to 0).toList.map(_.toString))
//     assertEquals(actual, expected)
//   }

//   test("Transformer.Accumulating.betweenCollections accumulates errors in the order they occur") {
//     val actual =
//       Transformer.Accumulating
//         .betweenCollections[AccumulatingFailure, Int, Positive, List, Vector]
//         .transform(List(-2, -1, 0))

//     val expected = Left((-2 to 0).toList.map(_.toString))
//     assertEquals(actual, expected)
//   }

//   test("Transformer.Accumulating.betweenCollections doesn't blow up the stack") {
//     val actual = Transformer.Accumulating
//       .betweenCollections[AccumulatingFailure, Int, Positive, List, Vector]
//       .transform(List.fill(1000000)(-1))

//     assert(actual.isLeft)
//   }

//   test("Transformer.Accumulating.betweenOptions returns None when input is None") {
//     val actual = Transformer.Accumulating.betweenOptions(using Positive.accTransformer, summon).transform(None)
//     assertEquals(actual, Right(None))
//   }

//   test("Transformer.Accumulating.betweenOptions returns Some when input is a Some and the transformation is successful") {
//     val actual = Transformer.Accumulating.betweenOptions(using Positive.accTransformer, summon).transform(Some(1))
//     assertEquals(actual, Right(Some(Positive(1))))
//   }

//   test("Transformer.Accumulating.betweenOptions fails when input is Some and the transformation fails") {
//     val actual = Transformer.Accumulating.betweenOptions(using Positive.accTransformer, summon).transform(Some(0))
//     assertEquals(actual, Left("0" :: Nil))
//   }

//   test("Transformer.Accumulating.betweenNonOptionOption returns Some when the transformation is successful") {
//     val actual = Transformer.Accumulating.betweenNonOptionOption(using Positive.accTransformer, summon).transform(1)
//     assertEquals(actual, Right(Some(Positive(1))))
//   }

//   test("Transformer.Accumulating.betweenNonOptionOption fails when the transformation fails") {
//     val actual = Transformer.Accumulating.betweenNonOptionOption(using Positive.accTransformer, summon).transform(0)
//     assertEquals(actual, Left("0" :: Nil))
//   }
// }
