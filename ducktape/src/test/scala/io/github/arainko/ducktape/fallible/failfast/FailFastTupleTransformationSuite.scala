package io.github.arainko.ducktape.fallible.failfast

import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.fallible.model.Positive

class FailFastTupleTransformationSuite extends DucktapeSuite {

  given Mode.FailFast.Either[String]()

  private def transformation(value: Int): Either[String, Positive] =
    Positive.failFastTransformer.transform(value)

  test("tuple-to-tuple works") {
    val source = (1, 1, List(1), (1, 2, 3))
    val expected = (1, Option(Positive(1)), Vector(Positive(1)), (Positive(1), Positive(2)))
    assertTransformsFallible(source, Right(expected))
  }

  test("tuple-to-product works") {
    case class Toplevel(int: Positive, opt: Option[Positive], coll: Vector[Positive], level1: Level1)
    case class Level1(int1: Positive, int2: Positive)

    val source = (1, 1, List(1), (1, 2, 3))
    val expected = Toplevel(Positive(1), Some(Positive(1)), Vector(Positive(1)), Level1(Positive(1), Positive(2)))
    assertTransformsFallible(source, Right(expected))
  }

  test("product-to-tuple works") {
    case class Toplevel(int: Int, opt: Int, coll: Vector[Int], level1: Level1)
    case class Level1(int1: Int, int2: Int, int3: Int)

    val source = Toplevel(1, 1, Vector(1), Level1(1, 2, 3))
    val expected = (Positive(1), Option(Positive(1)), List(Positive(1)), (Positive(1), Positive(2)))
    assertTransformsFallible(source, Right(expected))
  }

  test("tuple-to-function works") {
    def method(arg1: Positive, arg2: Option[Positive], coll: Vector[Positive], level1: (Positive, Positive)) =
      (arg1, arg2, coll, level1)
    val source: (Int, Int, List[Int], (Int, Int, Int)) = (1, 1, List(1), (1, 2, 3))
    val expected = (Positive(1), Some(Positive(1)), Vector(Positive(1)), (Positive(1), Positive(2)))

    assertEachEquals(
      source.fallibleVia(method),
      Transformer.defineVia[(Int, Int, List[Int], (Int, Int, Int))](method).fallible.build().transform(source)
    )(Right(expected))
  }

  test("tuple-to-tuple can be configured (dest side)") {
    val source: (Int, Int, List[Int], (Int, Int, Int)) = (1, 1, List(1), (1, 2, 3))
    val expected =
      (Positive(1), Option((Positive(1), Positive(2))), Vector(Positive(1)), (Positive(1), Positive(2), Positive(3), Positive(4)))

    assertTransformsFallibleConfigured(source, Right(expected))(
      Field.fallibleConst(_.apply(3).apply(3), transformation(4)),
      Field.fallibleComputed(_.apply(1).element, elem => transformation(elem._2).map(e => (e, Positive(2))))
    )
  }

  test("tuple-to-tuple can be configured (source side)") {
    enum Source {
      case One
      case Two
    }

    enum Dest {
      case One
      case Three
    }

    val source: (Int, Source, List[Source], (Source, Source, Source)) =
      (1, Source.One, List(Source.One), (Source.One, Source.Two, Source.Two))

    val expected: (Positive, Option[Dest], Vector[Dest], (Dest, Dest)) =
      (Positive(1), Option(Dest.One), Vector(Dest.One), (Dest.One, Dest.Three))

    assertTransformsFallibleConfigured(source, Right(expected))(
      Case.fallibleConst(_.apply(3).apply(1).at[Source.Two.type], Right(Dest.Three)),
      Case.fallibleConst(_.apply(3).apply(0).at[Source.Two.type], Right(Dest.Three)),
      Case.fallibleConst(_.apply(1).at[Source.Two.type], Right(Dest.Three)),
      Case.fallibleConst(_.apply(2).element.at[Source.Two.type], Right(Dest.Three))
    )
  }

  test("product-to-tuple can be configured (dest side)") {
    case class Toplevel(int: Int, opt: Int, coll: Vector[Int], level1: Level1)
    case class Level1(int1: Int, int2: Int, int3: Int)

    val source = Toplevel(1, 1, Vector(1), Level1(1, 2, 3))
    val expected =
      (Positive(1), Option((Positive(1), Positive(2))), Vector(Positive(1)), (Positive(1), Positive(2), Positive(3), Positive(4)))

    assertTransformsFallibleConfigured(source, Right(expected))(
      Field.fallibleConst(_.apply(3).apply(3), transformation(4)),
      Field.fallibleConst(_.apply(1).element, transformation(1).map(pos => (pos, Positive(2))))
    )
  }

  test("product-to-tuple can be configured (source side)") {
    case class Toplevel(int: Int, opt: Source, coll: Vector[Source], level1: Level1)
    case class Level1(int1: Source, int2: Source, int3: Source)

    enum Source {
      case One
      case Two
    }

    enum Dest {
      case One
      case Three
    }

    val source =
      Toplevel(1, Source.One, Vector(Source.One), Level1(Source.One, Source.Two, Source.Two))

    val expected: (Positive, Option[Dest], List[Dest], (Dest, Dest)) =
      (Positive(1), Option(Dest.One), List(Dest.One), (Dest.One, Dest.Three))

    assertTransformsFallibleConfigured(source, Right(expected))(
      Case.fallibleConst(_.level1.int2.at[Source.Two.type], Right(Dest.Three)),
      Case.fallibleConst(_.level1.int1.at[Source.Two.type], Right(Dest.Three)),
      Case.fallibleConst(_.coll.element.at[Source.Two.type], Right(Dest.Three)),
      Case.fallibleConst(_.opt.at[Source.Two.type], Right(Dest.Three))
    )
  }

  test("tuple-to-product can be configured (dest side)") {
    case class Toplevel(int: Positive, opt: Option[(Positive, Positive)], coll: Vector[Positive], level1: Level1)
    case class Level1(int1: Positive, int2: Positive, int3: Positive, int4: Positive)

    val source = (1, 1, List(1), (1, 2, 3))
    val expected = Toplevel(
      Positive(1),
      Some(Positive(1) -> Positive(2)),
      Vector(Positive(1)),
      Level1(Positive(1), Positive(2), Positive(3), Positive(4))
    )

    assertTransformsFallibleConfigured(source, Right(expected))(
      Field.fallibleConst(_.level1.int4, transformation(4)),
      Field.fallibleComputed(_.opt.element, elem => transformation(elem._2).map(e => (e, Positive(2))))
    )
  }

  test("tuple-to-product can be configured (source side)") {
    case class Toplevel(int: Positive, opt: Option[Dest], coll: Vector[Dest], level1: Level1)
    case class Level1(int1: Dest, int2: Dest)

    enum Dest {
      case One
      case Two
    }

    enum Source {
      case One
      case Three
    }

    val source: (Int, Source, List[Source], (Source, Source, Source)) =
      (1, Source.One, List(Source.One), (Source.One, Source.Three, Source.Three))

    val expected =
      Toplevel(Positive(1), Some(Dest.One), Vector(Dest.One), Level1(Dest.One, Dest.Two))

    assertTransformsFallibleConfigured(source, Right(expected))(
      Case.fallibleConst(_.apply(3).apply(1).at[Source.Three.type], Right(Dest.Two)),
      Case.fallibleConst(_.apply(3).apply(0).at[Source.Three.type], Right(Dest.Two)),
      Case.fallibleConst(_.apply(2).element.at[Source.Three.type], Right(Dest.Two)),
      Case.fallibleConst(_.apply(1).at[Source.Three.type], Right(Dest.Two))
    )
  }

  test("tuple-to-function can be configured (dest side)") {
    case class Toplevel(int: Positive, opt: Option[(Positive, Positive)], coll: Vector[Positive], level1: Level1)
    case class Level1(int1: Positive, int2: Positive, int3: Positive, int4: Positive)

    val source = (1, 1, List(1), (1, 2, 3))
    val expected = Toplevel(
      Positive(1),
      Some(Positive(1) -> Positive(2)),
      Vector(Positive(1)),
      Level1(Positive(1), Positive(2), Positive(3), Positive(4))
    )

    assertEachEquals(
      source
        .intoVia(Toplevel.apply)
        .fallible
        .transform(
          Field.fallibleConst(_.level1.int4, transformation(4)),
          Field.fallibleComputed(_.opt.element, elem => transformation(elem._2).map(e => (e, Positive(2))))
        ),
      Transformer
        .defineVia[(Int, Int, List[Int], (Int, Int, Int))](Toplevel.apply)
        .fallible
        .build(
          Field.fallibleConst(_.level1.int4, transformation(4)),
          Field.fallibleComputed(_.opt.element, elem => transformation(elem._2).map(e => (e, Positive(2))))
        )
        .transform(source)
    )(Right(expected))
  }

  test("tuple-to-function can be configured (source side)") {
    case class Toplevel(int: Positive, opt: Option[Dest], coll: Vector[Dest], level1: Level1)
    case class Level1(int1: Dest, int2: Dest)

    enum Dest {
      case One
      case Two
    }

    enum Source {
      case One
      case Three
    }

    val source: (Int, Source, List[Source], (Source, Source, Source)) =
      (1, Source.One, List(Source.One), (Source.One, Source.Three, Source.Three))

    val expected =
      Toplevel(Positive(1), Some(Dest.One), Vector(Dest.One), Level1(Dest.One, Dest.Two))

    assertEachEquals(
      source
        .intoVia(Toplevel.apply)
        .fallible
        .transform(
          Case.fallibleConst(_.apply(3).apply(1).at[Source.Three.type], Right(Dest.Two)),
          Case.fallibleConst(_.apply(3).apply(0).at[Source.Three.type], Right(Dest.Two)),
          Case.fallibleConst(_.apply(2).element.at[Source.Three.type], Right(Dest.Two)),
          Case.fallibleConst(_.apply(1).at[Source.Three.type], Right(Dest.Two))
        ),
      Transformer
        .defineVia[(Int, Source, List[Source], (Source, Source, Source))](Toplevel.apply)
        .fallible
        .build(
          Case.fallibleConst(_.apply(3).apply(1).at[Source.Three.type], Right(Dest.Two)),
          Case.fallibleConst(_.apply(3).apply(0).at[Source.Three.type], Right(Dest.Two)),
          Case.fallibleConst(_.apply(2).element.at[Source.Three.type], Right(Dest.Two)),
          Case.fallibleConst(_.apply(1).at[Source.Three.type], Right(Dest.Two))
        )
        .transform(source)
    )(Right(expected))
  }

  test("big tuples work") {
    val source = homogenousTupleOf[Int](26, index => index + 1)
    val expected = homogenousTupleOf[Positive](25, index => Positive(index + 1))

    assertTransformsFallible(source, Right(expected))
  }

  test("big tuples can be configured") {
    val source = homogenousTupleOf[Int](25, index => index + 1)
    val expected = homogenousTupleOf[Positive](26, index => Positive(index + 1))

    assertTransformsFallibleConfigured(source, Right(expected))(
      Field.fallibleConst(_.apply(25), transformation(26))
    )
  }
}
