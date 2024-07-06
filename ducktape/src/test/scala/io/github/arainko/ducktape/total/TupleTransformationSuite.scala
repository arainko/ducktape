package io.github.arainko.ducktape.total

import io.github.arainko.ducktape.*
import munit.{Compare, Location}

import scala.reflect.ClassTag

class TupleTransformationSuite extends DucktapeSuite {

  test("tuple-to-tuple works") {
    val source = (1, 1, List(1), (1, 2, 3))
    val expected = (1, Some(1), Vector(1), (1, 2))
    assertTransforms(source, expected)
  }

  test("tuple-to-product works") {
    case class Toplevel(int: Int, opt: Option[Int], coll: Vector[Int], level1: Level1)
    case class Level1(int1: Int, int2: Int)

    val source = (1, 1, List(1), (1, 2, 3))
    val expected = Toplevel(1, Some(1), Vector(1), Level1(1, 2))
    assertTransforms(source, expected)
  }

  test("product-to-tuple works") {
    case class Toplevel(int: Int, opt: Int, coll: Vector[Int], level1: Level1)
    case class Level1(int1: Int, int2: Int, int3: Int)

    val source = Toplevel(1, 1, Vector(1), Level1(1, 2, 3))
    val expected = (1, Option(1), List(1), (1, 2))
    assertTransforms(source, expected)
  }

  test("tuple-to-function works") {
    def method(arg1: Int, arg2: Option[Int], coll: Vector[Int], level1: (Int, Int)) = (arg1, arg2, coll, level1)
    val source: (Int, Int, List[Int], (Int, Int, Int)) = (1, 1, List(1), (1, 2, 3))
    val expected = (1, Some(1), Vector(1), (1, 2))

    assertEachEquals(
      source.via(method),
      Transformer.defineVia[(Int, Int, List[Int], (Int, Int, Int))](method).build().transform(source)
    )(expected)
  }

  test("tuple-to-tuple can be configured (dest side)") {
    val source = (1, 1, List(1), (1, 2, 3))
    val expected: (Int, Option[(Int, Int)], Vector[Int], (Int, Int, Int, Int)) = (1, Option((1, 2)), Vector(1), (1, 2, 3, 4))

    assertTransformsConfigured(source, expected)(
      Field.const(_.apply(3).apply(3), 4),
      Field.computed(_.apply(1).element, elem => (elem._2, 2))
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

    val expected: (Int, Option[Dest], Vector[Dest], (Dest, Dest)) =
      (1, Option(Dest.One), Vector(Dest.One), (Dest.One, Dest.Three))

    assertTransformsConfigured(source, expected)(
      Case.const(_.apply(3).apply(1).at[Source.Two.type], Dest.Three),
      Case.const(_.apply(3).apply(0).at[Source.Two.type], Dest.Three),
      Case.const(_.apply(1).at[Source.Two.type], Dest.Three),
      Case.const(_.apply(2).element.at[Source.Two.type], Dest.Three)
    )

  }

  test("product-to-tuple can be configured (dest side)") {
    case class Toplevel(int: Int, opt: Int, coll: Vector[Int], level1: Level1)
    case class Level1(int1: Int, int2: Int, int3: Int)

    val source = Toplevel(1, 1, Vector(1), Level1(1, 2, 3))
    val expected = (1, Option((1, 2)), Vector(1), (1, 2, 3, 4))

    assertTransformsConfigured(source, expected)(
      Field.const(_.apply(3).apply(3), 4),
      Field.const(_.apply(1).element, (1, 2))
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

    val expected: (Int, Option[Dest], List[Dest], (Dest, Dest)) =
      (1, Option(Dest.One), List(Dest.One), (Dest.One, Dest.Three))

    assertTransformsConfigured(source, expected)(
      Case.const(_.level1.int2.at[Source.Two.type], Dest.Three),
      Case.const(_.level1.int1.at[Source.Two.type], Dest.Three),
      Case.const(_.coll.element.at[Source.Two.type], Dest.Three),
      Case.const(_.opt.at[Source.Two.type], Dest.Three)
    )
  }

  test("tuple-to-product can be configured (dest side)") {
    case class Toplevel(int: Int, opt: Option[(Int, Int)], coll: Vector[Int], level1: Level1)
    case class Level1(int1: Int, int2: Int, int3: Int, int4: Int)

    val source = (1, 1, List(1), (1, 2, 3))
    val expected = Toplevel(1, Some(1 -> 2), Vector(1), Level1(1, 2, 3, 4))

    assertTransformsConfigured(source, expected)(
      Field.const(_.level1.int4, 4),
      Field.computed(_.opt.element, src => (src._2, 2))
    )
  }

  test("tuple-to-product can be configured (source side)") {
    case class Toplevel(int: Int, opt: Option[Dest], coll: Vector[Dest], level1: Level1)
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
      Toplevel(1, Some(Dest.One), Vector(Dest.One), Level1(Dest.One, Dest.Two))

    assertTransformsConfigured(source, expected)(
      Case.const(_.apply(3).apply(1).at[Source.Three.type], Dest.Two),
      Case.const(_.apply(3).apply(0).at[Source.Three.type], Dest.Two),
      Case.const(_.apply(2).element.at[Source.Three.type], Dest.Two),
      Case.const(_.apply(1).at[Source.Three.type], Dest.Two)
    )
  }

  test("tuple-to-function can be configured (dest side)") {
    case class Toplevel(int: Int, opt: Option[(Int, Int)], coll: Vector[Int], level1: Level1)
    case class Level1(int1: Int, int2: Int, int3: Int, int4: Int)

    val source: (Int, Int, List[Int], (Int, Int, Int)) = (1, 1, List(1), (1, 2, 3))
    val expected = Toplevel(1, Some(1 -> 2), Vector(1), Level1(1, 2, 3, 4))

    assertEachEquals(
      source
        .intoVia(Toplevel.apply)
        .transform(
          Field.const(_.level1.int4, 4),
          Field.computed(_.opt.element, src => (src._2, 2))
        ),
      Transformer
        .defineVia[(Int, Int, List[Int], (Int, Int, Int))](Toplevel.apply)
        .build(
          Field.const(_.level1.int4, 4),
          Field.computed(_.opt.element, src => (src._2, 2))
        )
        .transform(source)
    )(expected)
  }

  test("tuple-to-function can be configured (source side)") {
    case class Toplevel(int: Int, opt: Option[Dest], coll: Vector[Dest], level1: Level1)
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
      Toplevel(1, Some(Dest.One), Vector(Dest.One), Level1(Dest.One, Dest.Two))

    assertEachEquals(
      source
        .intoVia(Toplevel.apply)
        .transform(
          Case.const(_.apply(3).apply(1).at[Source.Three.type], Dest.Two),
          Case.const(_.apply(3).apply(0).at[Source.Three.type], Dest.Two),
          Case.const(_.apply(2).element.at[Source.Three.type], Dest.Two),
          Case.const(_.apply(1).at[Source.Three.type], Dest.Two)
        ),
      Transformer
        .defineVia[(Int, Source, List[Source], (Source, Source, Source))](Toplevel.apply)
        .build(
          Case.const(_.apply(3).apply(1).at[Source.Three.type], Dest.Two),
          Case.const(_.apply(3).apply(0).at[Source.Three.type], Dest.Two),
          Case.const(_.apply(2).element.at[Source.Three.type], Dest.Two),
          Case.const(_.apply(1).at[Source.Three.type], Dest.Two)
        )
        .transform(source)
    )(expected)
  }

  test("plain tuples can be configured with _-accessors") {

    val source = (1, 1, List(1), (1, 2, 3))
    val expected: (Int, Option[(Int, Int)], Vector[Int], (Int, Int, Int, Int)) = (1, Option((1, 2)), Vector(1), (1, 2, 3, 4))

    assertTransformsConfigured(source, expected)(
      Field.const(_._4._4, 4),
      Field.computed(_._2.element, elem => (elem._2, 2))
    )
  }

  test("big tuples work") {
    val source = homogenousTupleOf[Int](26, identity)
    val expected = homogenousTupleOf[Option[Int]](25, Some.apply)

    assertTransforms(source, expected)
  }

  test("big tuples can be configured") {
    val source = homogenousTupleOf[Int](25, identity)
    val expected = homogenousTupleOf[Option[Int]](26, Some.apply)

    assertTransformsConfigured(source, expected)(
      Field.const(_.apply(25), Some(25))
    )
  }

  test("Field.fallbackToDefault works for tuple-to-product") {
    case class Toplevel(int: Int, opt: Option[Int], coll: Vector[Int], level1: Level1)
    case class Level1(int1: Int, int2: Int, int3: Int, int4: Int = 4)

    val source = (1, 1, List(1), (1, 2, 3))
    val expected = Toplevel(1, Some(1), Vector(1), Level1(1, 2, 3, 4))

    assertTransformsConfigured(source, expected)(
      Field.fallbackToDefault,
    )
  }

  test("Field.fallbackToNone works") {
    case class Toplevel(int: Int, opt: Option[Int], coll: Vector[Int], level1: Level1)
    case class Level1(int1: Int, int2: Int, int3: Int, int4: Option[Int])

    val source = (1, 1, List(1), (1, 2, 3))
    val expected = Toplevel(1, Some(1), Vector(1), Level1(1, 2, 3, None))

    assertTransformsConfigured(source, expected)(
      Field.fallbackToNone,
    )
  }

  test("Field.default works for tuple-to-product") {
    case class Toplevel(int: Int, opt: Option[Int], coll: Vector[Int], level1: Level1)
    case class Level1(int1: Int, int2: Int, int3: Int, int4: Int = 4)

    val source = (1, 1, List(1), (1, 2, 3))
    val expected = Toplevel(1, Some(1), Vector(1), Level1(1, 2, 3, 4))

    assertTransformsConfigured(source, expected)(
      Field.default(_.level1.int4),
    )
  }
}
