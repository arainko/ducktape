package io.github.arainko.ducktape.fallible.accumulating

import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.fallible.model.Positive
import io.github.arainko.ducktape.internal.*

import scala.annotation.nowarn

class AccumulatingNestedConfigurationSuite extends DucktapeSuite {
  private given F: Mode.Accumulating.Either[String, List] with Mode.Capability.ContextAware with {
    override def embedContext[A](path: String, fa: Either[List[String], A]): Either[List[String], A] =
      fa.left.map(_.map(err => s"$err @ $path"))
  }

  def fallibleComputation(value: Int): Either[List[String], Positive] =
    Left(List("wow"))

  test("nested product fields can be configured") {
    final case class SourceToplevel1(level1: SourceLevel1)
    final case class SourceLevel1(level2: SourceLevel2)
    final case class SourceLevel2(int: Int)

    final case class DestToplevel1(level1: DestLevel1)
    final case class DestLevel1(level2: DestLevel2)
    final case class DestLevel2(int: Positive, extra: Positive)

    val source = SourceToplevel1(SourceLevel1(SourceLevel2(1)))
    val expected = DestToplevel1(DestLevel1(DestLevel2(Positive(1), Positive(2137))))

    assertEachEquals(
      internal.CodePrinter.code:
        source
          .into[DestToplevel1]
          .fallible
          .transform(Field.fallibleConst(_.level1.level2.extra, fallibleComputation(2137)))
      ,
      source
        .intoVia(DestToplevel1.apply)
        .fallible
        .transform(Field.fallibleConst(_.level1.level2.extra, fallibleComputation(2137))),
      Transformer
        .define[SourceToplevel1, DestToplevel1]
        .fallible
        .build(Field.fallibleConst(_.level1.level2.extra, fallibleComputation(2137)))
        .transform(source),
      Transformer
        .defineVia[SourceToplevel1](DestToplevel1.apply)
        .fallible
        .build(Field.fallibleConst(_.level1.level2.extra, fallibleComputation(2137)))
        .transform(source)
    )(F.pure(expected))
  }

  test("nested product fields inside nested coproduct cases can be configured") {
    final case class SourceToplevel1(level1: SourceLevel1)
    enum SourceLevel1 {
      case Level1(level2: SourceLevel2)
    }
    final case class SourceLevel2(int: Int)

    final case class DestToplevel1(level1: DestLevel1)
    enum DestLevel1 {
      case Level1(level2: DestLevel2)
    }
    final case class DestLevel2(int: Positive, extra: Positive)

    val source = SourceToplevel1(SourceLevel1.Level1(SourceLevel2(1)))
    val expected = DestToplevel1(DestLevel1.Level1(DestLevel2(Positive(1), Positive(2137))))

    assertEachEquals(
      source
        .into[DestToplevel1]
        .fallible
        .transform(Field.fallibleConst(_.level1.at[DestLevel1.Level1].level2.extra, fallibleComputation(2137))),
      source
        .intoVia(DestToplevel1.apply)
        .fallible
        .transform(Field.fallibleConst(_.level1.at[DestLevel1.Level1].level2.extra, fallibleComputation(2137))),
      Transformer
        .define[SourceToplevel1, DestToplevel1]
        .fallible
        .build(Field.fallibleConst(_.level1.at[DestLevel1.Level1].level2.extra, fallibleComputation(2137)))
        .transform(source),
      Transformer
        .defineVia[SourceToplevel1](DestToplevel1.apply)
        .fallible
        .build(Field.fallibleConst(_.level1.at[DestLevel1.Level1].level2.extra, fallibleComputation(2137)))
        .transform(source)
    )(F.pure(expected))
  }

  test("nested product fields can be overriden") {
    final case class SourceToplevel1(level1: SourceLevel1)
    final case class SourceLevel1(level2: SourceLevel2)
    final case class SourceLevel2(int: Int)

    final case class DestToplevel1(level1: DestLevel1)
    final case class DestLevel1(level2: DestLevel2)
    final case class DestLevel2(int: Positive)

    val source = SourceToplevel1(SourceLevel1(SourceLevel2(1)))
    val expected = DestToplevel1(DestLevel1(DestLevel2(Positive(50))))

    assertEachEquals(
      source.into[DestToplevel1].fallible.transform(Field.fallibleConst(_.level1.level2.int, fallibleComputation(50))),
      source.intoVia(DestToplevel1.apply).fallible.transform(Field.fallibleConst(_.level1.level2.int, fallibleComputation(50))),
      Transformer
        .define[SourceToplevel1, DestToplevel1]
        .fallible
        .build(Field.fallibleConst(_.level1.level2.int, fallibleComputation(50)))
        .transform(source),
      Transformer
        .defineVia[SourceToplevel1](DestToplevel1.apply)
        .fallible
        .build(Field.fallibleConst(_.level1.level2.int, fallibleComputation(50)))
        .transform(source)
    )(F.pure(expected))
  }

  test("nested product fields can be configured by overriding the transformation that is a level above") {
    final case class SourceToplevel1(level1: SourceLevel1)
    final case class SourceLevel1(level2: SourceLevel2)
    final case class SourceLevel2(int: Int)

    final case class DestToplevel1(level1: DestLevel1)
    final case class DestLevel1(level2: DestLevel2)
    final case class DestLevel2(int: Positive, extra: Positive)

    val source = SourceToplevel1(SourceLevel1(SourceLevel2(1)))
    val expected = DestToplevel1(DestLevel1(DestLevel2(Positive(50), Positive(50))))

    assertEachEquals(
      source
        .into[DestToplevel1]
        .fallible
        .transform(Field.fallibleConst(_.level1.level2, F.map(fallibleComputation(50), pos => DestLevel2(pos, pos)))),
      source
        .intoVia(DestToplevel1.apply)
        .fallible
        .transform(Field.fallibleConst(_.level1.level2, F.map(fallibleComputation(50), pos => DestLevel2(pos, pos)))),
      Transformer
        .define[SourceToplevel1, DestToplevel1]
        .fallible
        .build(Field.fallibleConst(_.level1.level2, F.map(fallibleComputation(50), pos => DestLevel2(pos, pos))))
        .transform(source),
      Transformer
        .defineVia[SourceToplevel1](DestToplevel1.apply)
        .fallible
        .build(Field.fallibleConst(_.level1.level2, F.map(fallibleComputation(50), pos => DestLevel2(pos, pos))))
        .transform(source)
    )(F.pure(expected))
  }

  test("nested product configuration fails if the types do not line up") {
    final case class SourceToplevel1(level1: SourceLevel1)
    final case class SourceLevel1(level2: SourceLevel2)
    final case class SourceLevel2(int: Int)

    final case class DestToplevel1(level1: DestLevel1)
    final case class DestLevel1(level2: DestLevel2)
    final case class DestLevel2(int: Positive, extra: Positive)

    val source = SourceToplevel1(SourceLevel1(SourceLevel2(1)))

    assertFailsToCompileWith {
      """
      source.into[DestToplevel1].fallible.transform(Field.fallibleConst(_.level1.level2.extra, F.pure("waaa")))
      """
    }(
      "Configuration is not valid since the provided type (java.lang.String) is not a subtype of io.github.arainko.ducktape.fallible.model.Positive @ DestToplevel1.level1.level2.extra",
      "No field 'extra' found in SourceLevel2 @ DestToplevel1.level1.level2.extra"
    )
  }: @nowarn("msg=unused local definition")

  test("Field.fallibleComputed works for nested fields") {
    final case class SourceToplevel1(level1: SourceLevel1)
    final case class SourceLevel1(level2: SourceLevel2)
    final case class SourceLevel2(int: Int)

    final case class DestToplevel1(level1: DestLevel1)
    final case class DestLevel1(level2: DestLevel2)
    final case class DestLevel2(int: Positive, extra: Positive)

    val source = SourceToplevel1(SourceLevel1(SourceLevel2(1)))
    val expected = DestToplevel1(DestLevel1(DestLevel2(Positive(1), Positive(2137))))

    assertEachEquals(
      source
        .into[DestToplevel1]
        .fallible
        .transform(
          Field.fallibleComputed(_.level1.level2.extra, a => fallibleComputation(a.level1.level2.int + 2136))
        ),
      source
        .intoVia(DestToplevel1.apply)
        .fallible
        .transform(Field.fallibleComputed(_.level1.level2.extra, a => fallibleComputation(a.level1.level2.int + 2136))),
      Transformer
        .define[SourceToplevel1, DestToplevel1]
        .fallible
        .build(Field.fallibleComputed(_.level1.level2.extra, a => fallibleComputation(a.level1.level2.int + 2136)))
        .transform(source),
      Transformer
        .defineVia[SourceToplevel1](DestToplevel1.apply)
        .fallible
        .build(Field.fallibleComputed(_.level1.level2.extra, a => fallibleComputation(a.level1.level2.int + 2136)))
        .transform(source)
    )(F.pure(expected))
  }

  test("nested product fields with collection and option elements can be configured") {
    final case class SourceToplevel1(level1: List[SourceLevel1])
    final case class SourceLevel1(level2: Option[SourceLevel2])
    final case class SourceLevel2(level3: SourceLevel3)
    final case class SourceLevel3(int: Int)

    final case class DestToplevel1(level1: Vector[DestLevel1])
    final case class DestLevel1(level2: Option[DestLevel2])
    final case class DestLevel2(level3: Option[DestLevel3])
    final case class DestLevel3(int: Positive, extra: Positive)

    val source = SourceToplevel1(List(SourceLevel1(Some(SourceLevel2(SourceLevel3(1))))))
    val expected = DestToplevel1(Vector(DestLevel1(Some(DestLevel2(Some(DestLevel3(Positive(1), Positive(2137))))))))

    assertEachEquals(
      source
        .into[DestToplevel1]
        .fallible
        .transform(
          Field.fallibleConst(_.level1.element.level2.element.level3.element.extra, fallibleComputation(2137))
        ),

      // TODO: compiler crashes here :( minimize and report to dotty
      // source
      //   .intoVia(DestToplevel1.apply)
      //   .fallible
      //   .transform(
      //     Field.fallibleConst(_.level1.element.level2.element.level3.element.extra, fallibleComputation(2137))
      //   ),

      Transformer
        .define[SourceToplevel1, DestToplevel1]
        .fallible
        .build(Field.fallibleConst(_.level1.element.level2.element.level3.element.extra, fallibleComputation(2137)))
        .transform(source),
      Transformer
        .defineVia[SourceToplevel1](DestToplevel1.apply)
        .fallible
        .build(Field.fallibleConst(_.level1.element.level2.element.level3.element.extra, fallibleComputation(2137)))
        .transform(source)
    )(F.pure(expected))
  }

  test("nested product fields with collection and option elements can be overridden") {
    final case class SourceToplevel1(level1: List[SourceLevel1])
    final case class SourceLevel1(level2: Option[SourceLevel2])
    final case class SourceLevel2(level3: SourceLevel3)
    final case class SourceLevel3(int: Int)

    final case class DestToplevel1(level1: Vector[DestLevel1])
    final case class DestLevel1(level2: Option[DestLevel2])
    final case class DestLevel2(level3: Option[DestLevel3])
    final case class DestLevel3(int: Positive, extra: Positive)

    val source = SourceToplevel1(List(SourceLevel1(Some(SourceLevel2(SourceLevel3(1))))))
    val expected = DestToplevel1(Vector(DestLevel1(Some(DestLevel2(Some(DestLevel3(Positive(2), Positive(2))))))))

    assertEachEquals(
      source
        .into[DestToplevel1]
        .fallible
        .transform(
          Field.fallibleConst(
            _.level1.element.level2.element.level3.element,
            F.map(fallibleComputation(2), pos => DestLevel3(pos, pos))
          )
        ),
      // TODO: Compiler crash here as well :(
      // source
      //   .intoVia(DestToplevel1.apply)
      //   .fallible
      //   .transform(
      //     Field.fallibleConst(
      //       _.level1.element.level2.element.level3.element,
      //       F.map(fallibleComputation(2), pos => DestLevel3(pos, pos))
      //     )
      //   ),
      Transformer
        .define[SourceToplevel1, DestToplevel1]
        .fallible
        .build(
          Field.fallibleConst(
            _.level1.element.level2.element.level3.element,
            F.map(fallibleComputation(2), pos => DestLevel3(pos, pos))
          )
        )
        .transform(source),
      Transformer
        .defineVia[SourceToplevel1](DestToplevel1.apply)
        .fallible
        .build(
          Field.fallibleConst(
            _.level1.element.level2.element.level3.element,
            F.map(fallibleComputation(2), pos => DestLevel3(pos, pos))
          )
        )
        .transform(source)
    )(F.pure(expected))
  }

  test("nested coproduct cases can be configured") {
    enum SourceToplevel1 {
      case Level1(level2: SourceLevel2)
    }

    enum SourceLevel2 {
      case Level2(level3: SourceLevel3)
    }

    enum SourceLevel3 {
      case One
      case Two
      case Extra
    }

    enum DestToplevel1 {
      case Level1(level2: DestLevel2)
    }

    enum DestLevel2 {
      case Level2(level3: DestLevel3)
    }

    enum DestLevel3 {
      case One
      case Two
    }

    val source = SourceToplevel1.Level1(SourceLevel2.Level2(SourceLevel3.Extra))
    val expected = DestToplevel1.Level1(DestLevel2.Level2(DestLevel3.One))

    // scalafmt: { maxColumn = 150 }
    assertEachEquals(
      source
        .into[DestToplevel1]
        .fallible
        .transform(
          Case.fallibleConst(_.at[SourceToplevel1.Level1].level2.at[SourceLevel2.Level2].level3.at[SourceLevel3.Extra.type], F.pure(DestLevel3.One))
        ),
      Transformer
        .define[SourceToplevel1, DestToplevel1]
        .fallible
        .build(
          Case.fallibleConst(_.at[SourceToplevel1.Level1].level2.at[SourceLevel2.Level2].level3.at[SourceLevel3.Extra.type], F.pure(DestLevel3.One))
        )
        .transform(source)
    )(F.pure(expected))
  }

  test("nested coproduct cases inside nested fields can be configured") {
    final case class SourceToplevel1(level1: SourceLevel1)

    enum SourceLevel1 {
      case Level1(level2: SourceLevel2)
    }

    enum SourceLevel2 {
      case Level2(level3: SourceLevel3)
    }

    enum SourceLevel3 {
      case One
      case Two
      case Extra
    }

    final case class DestToplevel1(level1: DestLevel1)

    enum DestLevel1 {
      case Level1(level2: DestLevel2)
    }

    enum DestLevel2 {
      case Level2(level3: DestLevel3)
    }

    enum DestLevel3 {
      case One
      case Two
    }

    val source = SourceToplevel1(SourceLevel1.Level1(SourceLevel2.Level2(SourceLevel3.Extra)))
    val expected = DestToplevel1(DestLevel1.Level1(DestLevel2.Level2(DestLevel3.One)))

    assertEachEquals(
      source
        .into[DestToplevel1]
        .fallible
        .transform(
          Case
            .fallibleConst(_.level1.at[SourceLevel1.Level1].level2.at[SourceLevel2.Level2].level3.at[SourceLevel3.Extra.type], F.pure(DestLevel3.One))
        ),
      source
        .intoVia(DestToplevel1.apply)
        .fallible
        .transform(
          Case
            .fallibleConst(_.level1.at[SourceLevel1.Level1].level2.at[SourceLevel2.Level2].level3.at[SourceLevel3.Extra.type], F.pure(DestLevel3.One))
        ),
      Transformer
        .define[SourceToplevel1, DestToplevel1]
        .fallible
        .build(
          Case
            .fallibleConst(_.level1.at[SourceLevel1.Level1].level2.at[SourceLevel2.Level2].level3.at[SourceLevel3.Extra.type], F.pure(DestLevel3.One))
        )
        .transform(source),
      Transformer
        .defineVia[SourceToplevel1](DestToplevel1.apply)
        .fallible
        .build(
          Case
            .fallibleConst(_.level1.at[SourceLevel1.Level1].level2.at[SourceLevel2.Level2].level3.at[SourceLevel3.Extra.type], F.pure(DestLevel3.One))
        )
        .transform(source)
    )(F.pure(expected))
  }

  test("nested coproduct cases can be overridden") {
    enum SourceToplevel1 {
      case Level1(level2: SourceLevel2)
    }

    enum SourceLevel2 {
      case Level2(level3: SourceLevel3)
    }

    enum SourceLevel3 {
      case One
      case Two
    }

    enum DestToplevel1 {
      case Level1(level2: DestLevel2)
    }

    enum DestLevel2 {
      case Level2(level3: DestLevel3)
    }

    enum DestLevel3 {
      case One
      case Two
    }

    val source = SourceToplevel1.Level1(SourceLevel2.Level2(SourceLevel3.One))
    val expected = DestToplevel1.Level1(DestLevel2.Level2(DestLevel3.Two))

    // scalafmt: { maxColumn = 150 }
    assertEachEquals(
      source
        .into[DestToplevel1]
        .fallible
        .transform(
          Case.fallibleConst(_.at[SourceToplevel1.Level1].level2.at[SourceLevel2.Level2].level3.at[SourceLevel3.One.type], F.pure(DestLevel3.Two))
        ),
      Transformer
        .define[SourceToplevel1, DestToplevel1]
        .fallible
        .build(
          Case.fallibleConst(_.at[SourceToplevel1.Level1].level2.at[SourceLevel2.Level2].level3.at[SourceLevel3.One.type], F.pure(DestLevel3.Two))
        )
        .transform(source)
    )(F.pure(expected))
  }

  test("nested coproduct cases can be configured by configuring the case that is a level above") {
    enum SourceToplevel1 {
      case Level1(level2: SourceLevel2)
    }

    enum SourceLevel2 {
      case Level2(level3: SourceLevel3)
    }

    enum SourceLevel3 {
      case One
      case Two
      case Extra
    }

    enum DestToplevel1 {
      case Level1(level2: DestLevel2)
    }

    enum DestLevel2 {
      case Level2(level3: DestLevel3)
    }

    enum DestLevel3 {
      case One
      case Two
    }

    val source = SourceToplevel1.Level1(SourceLevel2.Level2(SourceLevel3.Extra))
    val expected = DestToplevel1.Level1(DestLevel2.Level2(DestLevel3.One))

    assertEachEquals(
      source
        .into[DestToplevel1]
        .fallible
        .transform(
          Case.fallibleConst(_.at[SourceToplevel1.Level1].level2.at[SourceLevel2.Level2], F.pure(DestLevel2.Level2(DestLevel3.One)))
        ),
      Transformer
        .define[SourceToplevel1, DestToplevel1]
        .fallible
        .build(
          Case.fallibleConst(_.at[SourceToplevel1.Level1].level2.at[SourceLevel2.Level2], F.pure(DestLevel2.Level2(DestLevel3.One)))
        )
        .transform(source)
    )(F.pure(expected))
  }

  test("nested coproduct configuration fails if the types do not line up") {
    enum SourceToplevel1 {
      case Level1(level2: SourceLevel2)
    }

    enum SourceLevel2 {
      case Level2(level3: SourceLevel3)
    }

    enum SourceLevel3 {
      case One
      case Two
      case Extra
    }

    enum DestToplevel1 {
      case Level1(level2: DestLevel2)
    }

    enum DestLevel2 {
      case Level2(level3: DestLevel3)
    }

    enum DestLevel3 {
      case One
      case Two
    }

    val source = SourceToplevel1.Level1(SourceLevel2.Level2(SourceLevel3.Extra))

    assertFailsToCompileWith {
      """
      source
        .into[DestToplevel1]
        .fallible
        .transform(
          Case.fallibleConst(_.at[SourceToplevel1.Level1].level2.at[SourceLevel2.Level2].level3.at[SourceLevel3.Extra.type], F.pure(123))
        )
      """
    }(
      "No child named 'Extra' found in DestLevel3 @ SourceToplevel1.at[SourceToplevel1.Level1].level2.at[SourceLevel2.Level2].level3.at[SourceLevel3.Extra.type]",
      "Configuration is not valid since the provided type (scala.Int) is not a subtype of DestLevel3 @ SourceToplevel1.at[SourceToplevel1.Level1].level2.at[SourceLevel2.Level2].level3.at[SourceLevel3.Extra.type]"
    )
  }: @nowarn("msg=unused local definition")

  test("nested coproduct cases with collection and option elements can be configured") {
    enum SourceToplevel1 {
      case Level1(level2: Option[SourceLevel2])
    }

    enum SourceLevel2 {
      case Level2(level3: List[SourceLevel3])
    }

    enum SourceLevel3 {
      case Level3(level4: SourceLevel4)
    }

    enum SourceLevel4 {
      case One
      case Two
      case Extra
    }

    enum DestToplevel1 {
      case Level1(level2: Option[DestLevel2])
    }

    enum DestLevel2 {
      case Level2(level3: Vector[DestLevel3])
    }

    enum DestLevel3 {
      case Level3(level4: Option[DestLevel4])
    }

    enum DestLevel4 {
      case One
      case Two
    }

    val source = SourceToplevel1.Level1(Some(SourceLevel2.Level2(List(SourceLevel3.Level3(SourceLevel4.Extra)))))
    val expected = DestToplevel1.Level1(Some(DestLevel2.Level2(Vector(DestLevel3.Level3(Some(DestLevel4.One))))))

    // scalafmt: { maxColumn = 150 }
    assertEachEquals(
      // TODO: Compiler crash when using an internal class, works when Source and Dest are declared as toplevel
      // source
      //   .into[DestToplevel1]
      //   .fallible
      //   .transform(
      //     Case.fallibleConst(
      //       _.at[SourceToplevel1.Level1].level2.element
      //         .at[SourceLevel2.Level2]
      //         .level3
      //         .element
      //         .at[SourceLevel3.Level3]
      //         .level4
      //         .at[SourceLevel4.Extra.type],
      //       F.pure(DestLevel4.One)
      //     )
      //   ),
      Transformer
        .define[SourceToplevel1, DestToplevel1]
        .fallible
        .build(
          Case.fallibleConst(
            _.at[SourceToplevel1.Level1].level2.element
              .at[SourceLevel2.Level2]
              .level3
              .element
              .at[SourceLevel3.Level3]
              .level4
              .at[SourceLevel4.Extra.type],
            F.pure(DestLevel4.One)
          )
        )
        .transform(source)
    )(F.pure(expected))
  }

  test("nested coproduct cases with the NonOptionOption transformation variant can be configured") {
    enum SourceToplevel1 {
      case Level1(level2: SourceLevel2)
    }

    enum SourceLevel2 {
      case One, Two, Extra
    }

    enum DestToplevel1 {
      case Level1(level2: Option[DestLevel2])
    }

    enum DestLevel2 {
      case One
      case Two
    }

    val source = SourceToplevel1.Level1(SourceLevel2.Extra)
    val expected = DestToplevel1.Level1(Some(DestLevel2.Two))

    // scalafmt: { maxColumn = 150 }
    assertEachEquals(
      source
        .into[DestToplevel1]
        .fallible
        .transform(
          Case.fallibleConst(_.at[SourceToplevel1.Level1].level2.at[SourceLevel2.Extra.type], F.pure(DestLevel2.Two))
        ),
      Transformer
        .define[SourceToplevel1, DestToplevel1]
        .fallible
        .build(Case.fallibleConst(_.at[SourceToplevel1.Level1].level2.at[SourceLevel2.Extra.type], F.pure(DestLevel2.Two)))
        .transform(source)
    )(F.pure(expected))
  }

  test("Case.fallibleComputed works for nested cases") {
    enum SourceToplevel1 {
      case Level1(level2: SourceLevel2)
    }

    enum SourceLevel2 {
      case Level2(level3: SourceLevel3)
    }

    enum SourceLevel3 {
      case One(int: Int)
      case Two(str: String)
      case Extra(int: Int)
    }

    enum DestToplevel1 {
      case Level1(level2: DestLevel2)
    }

    enum DestLevel2 {
      case Level2(level3: DestLevel3)
    }

    enum DestLevel3 {
      case One(int: Int)
      case Two(str: String)
    }

    val source = SourceToplevel1.Level1(SourceLevel2.Level2(SourceLevel3.Extra(1)))
    val expected = DestToplevel1.Level1(DestLevel2.Level2(DestLevel3.One(6)))

    assertEachEquals(
      source
        .into[DestToplevel1]
        .fallible
        .transform(
          Case.fallibleComputed(
            _.at[SourceToplevel1.Level1].level2.at[SourceLevel2.Level2].level3.at[SourceLevel3.Extra],
            extra => F.pure(DestLevel3.One(extra.int + 5))
          )
        ),
      Transformer
        .define[SourceToplevel1, DestToplevel1]
        .fallible
        .build(
          Case.fallibleComputed(
            _.at[SourceToplevel1.Level1].level2.at[SourceLevel2.Level2].level3.at[SourceLevel3.Extra],
            extra => F.pure(DestLevel3.One(extra.int + 5))
          )
        )
        .transform(source)
    )(F.pure(expected))
  }

  test("Fails when a Case config doesn't end with an 'at' segment") {
    enum SourceToplevel1 {
      case Level1(level2: SourceLevel2)
    }

    enum SourceLevel2 {
      case Level2(level3: SourceLevel3)
    }

    enum SourceLevel3 {
      case One(int: Int)
      case Two(str: String)
      case Extra(int: Int)
    }

    enum DestToplevel1 {
      case Level1(level2: DestLevel2)
    }

    enum DestLevel2 {
      case Level2(level3: DestLevel3)
    }

    enum DestLevel3 {
      case One(int: Int)
      case Two(str: String)
    }

    assertFailsToCompileWith(
      """
      val source = SourceToplevel1.Level1(SourceLevel2.Level2(SourceLevel3.Extra(1)))

      source
        .into[DestToplevel1]
        .fallible
        .transform(
          Case.fallibleComputed(_.at[SourceToplevel1.Level1].level2, _ => F.pure(???))
        )
      """
    )(
      "No child named 'Extra' found in DestLevel3 @ SourceToplevel1.at[SourceToplevel1.Level1].level2.at[SourceLevel2.Level2].level3.at[SourceLevel3.Extra]",
      "Case config's path should always end with an `.at` segment @ SourceToplevel1"
    )

  }

  test("Field.fallbackToNone works") {
    final case class SourceToplevel(level1: SourceLevel1)
    final case class SourceLevel1(str: String, int: Int)

    final case class DestToplevel(extra: Option[Positive], level1: DestLevel1)
    final case class DestLevel1(extra: Option[Positive], str: String, int: Positive)

    val source = SourceToplevel(SourceLevel1("str", 1))
    val expected = DestToplevel(None, DestLevel1(None, "str", Positive(1)))

    assertEachEquals(
      source.into[DestToplevel].fallible.transform(Field.fallbackToNone),
      source.intoVia(DestToplevel.apply).fallible.transform(Field.fallbackToNone),
      Transformer.define[SourceToplevel, DestToplevel].fallible.build(Field.fallbackToNone).transform(source),
      Transformer.defineVia[SourceToplevel](DestToplevel.apply).fallible.build(Field.fallbackToNone).transform(source)
    )(F.pure(expected))
  }

  test("Field.fallbackToNone.regional works") {
    final case class SourceToplevel(level1: SourceLevel1)
    final case class SourceLevel1(str: String, int: Int)

    final case class DestToplevel(extra: Option[Positive], level1: DestLevel1)
    final case class DestLevel1(extra: Option[Positive], str: String, int: Positive)

    val source = SourceToplevel(SourceLevel1("str", 1))
    val expected = DestToplevel(Some(Positive(123)), DestLevel1(None, "str", Positive(1)))

    assertEachEquals(
      source
        .into[DestToplevel]
        .fallible
        .transform(
          Field.fallbackToNone.regional(_.level1),
          Field.fallibleConst(_.extra, F.map(fallibleComputation(123), pos => Some(pos)))
        ),
      source
        .intoVia(DestToplevel.apply)
        .fallible
        .transform(
          Field.fallbackToNone.regional(_.level1),
          Field.fallibleConst(_.extra, F.map(fallibleComputation(123), pos => Some(pos)))
        ),
      Transformer
        .define[SourceToplevel, DestToplevel]
        .fallible
        .build(
          Field.fallbackToNone.regional(_.level1),
          Field.fallibleConst(_.extra, F.map(fallibleComputation(123), pos => Some(pos)))
        )
        .transform(source),
      Transformer
        .defineVia[SourceToplevel](DestToplevel.apply)
        .fallible
        .build(
          Field.fallbackToNone.regional(_.level1),
          Field.fallibleConst(_.extra, F.map(fallibleComputation(123), pos => Some(pos)))
        )
        .transform(source)
    )(F.pure(expected))
  }

  test("Field.fallbackToDefault works") {
    final case class SourceToplevel(level1: SourceLevel1)
    final case class SourceLevel1(str: String, int: Int)

    final case class DestToplevel(extra: Positive = Positive(111), level1: DestLevel1)
    final case class DestLevel1(extra: Positive = Positive(123), str: String, int: Positive)

    val source = SourceToplevel(SourceLevel1("str", 1))
    val expected = DestToplevel(Positive(111), DestLevel1(Positive(123), "str", Positive(1)))

    assertEachEquals(
      source.into[DestToplevel].fallible.transform(Field.fallbackToDefault),
      Transformer.define[SourceToplevel, DestToplevel].fallible.build(Field.fallbackToDefault).transform(source)
    )(F.pure(expected))
  }

//   // TODO: More testing for this, eg. for a generic case class with a default for the generic field
  test("Field.fallbackToDefault.regional works") {
    final case class SourceToplevel(level1: SourceLevel1)
    final case class SourceLevel1(str: String, int: Int)

    final case class DestToplevel(extra: Positive = Positive(111), level1: DestLevel1)
    final case class DestLevel1(extra: Positive = Positive(123), str: String, int: Positive)

    val source = SourceToplevel(SourceLevel1("str", 1))
    val expected = DestToplevel(Positive(123), DestLevel1(Positive(123), "str", Positive(1)))

    assertEachEquals(
      source
        .into[DestToplevel]
        .fallible
        .transform(
          Field.fallbackToDefault.regional(_.level1),
          Field.fallibleConst(_.extra, fallibleComputation(123))
        ),
      Transformer
        .define[SourceToplevel, DestToplevel]
        .fallible
        .build(
          Field.fallbackToDefault.regional(_.level1),
          Field.fallibleConst(_.extra, fallibleComputation(123))
        )
        .transform(source)
    )(F.pure(expected))
  }
}
