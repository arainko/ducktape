package io.github.arainko.ducktape.total

import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.internal.*

import scala.annotation.nowarn

class NestedConfigurationSuite extends DucktapeSuite {
  test("nested product fields can be configured") {
    final case class SourceToplevel1(level1: SourceLevel1)
    final case class SourceLevel1(level2: SourceLevel2)
    final case class SourceLevel2(int: Int)

    final case class DestToplevel1(level1: DestLevel1)
    final case class DestLevel1(level2: DestLevel2)
    final case class DestLevel2(int: Int, extra: String)

    val source = SourceToplevel1(SourceLevel1(SourceLevel2(1)))
    val expected = DestToplevel1(DestLevel1(DestLevel2(1, "CONFIGURED")))

    assertEachEquals(
      source.into[DestToplevel1].transform(Field.const(_.level1.level2.extra, "CONFIGURED")),
      source.intoVia(DestToplevel1.apply).transform(Field.const(_.level1.level2.extra, "CONFIGURED")),
      Transformer
        .define[SourceToplevel1, DestToplevel1]
        .build(Field.const(_.level1.level2.extra, "CONFIGURED"))
        .transform(source),
      Transformer
        .defineVia[SourceToplevel1](DestToplevel1.apply)
        .build(Field.const(_.level1.level2.extra, "CONFIGURED"))
        .transform(source)
    )(expected)
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
    final case class DestLevel2(int: Int, extra: String)

    val source = SourceToplevel1(SourceLevel1.Level1(SourceLevel2(1)))
    val expected = DestToplevel1(DestLevel1.Level1(DestLevel2(1, "CONFIGURED")))

    assertEachEquals(
      source.into[DestToplevel1].transform(Field.const(_.level1.at[DestLevel1.Level1].level2.extra, "CONFIGURED")),
      source.intoVia(DestToplevel1.apply).transform(Field.const(_.level1.at[DestLevel1.Level1].level2.extra, "CONFIGURED")),
      Transformer
        .define[SourceToplevel1, DestToplevel1]
        .build(Field.const(_.level1.at[DestLevel1.Level1].level2.extra, "CONFIGURED"))
        .transform(source),
      Transformer
        .defineVia[SourceToplevel1](DestToplevel1.apply)
        .build(Field.const(_.level1.at[DestLevel1.Level1].level2.extra, "CONFIGURED"))
        .transform(source)
    )(expected)
  }

  test("nested product fields can be overriden") {
    final case class SourceToplevel1(level1: SourceLevel1)
    final case class SourceLevel1(level2: SourceLevel2)
    final case class SourceLevel2(int: Int)

    final case class DestToplevel1(level1: DestLevel1)
    final case class DestLevel1(level2: DestLevel2)
    final case class DestLevel2(int: Int)

    val source = SourceToplevel1(SourceLevel1(SourceLevel2(1)))
    val expected = DestToplevel1(DestLevel1(DestLevel2(50)))

    assertEachEquals(
      source.into[DestToplevel1].transform(Field.const(_.level1.level2.int, 50)),
      source.intoVia(DestToplevel1.apply).transform(Field.const(_.level1.level2.int, 50)),
      Transformer
        .define[SourceToplevel1, DestToplevel1]
        .build(Field.const(_.level1.level2.int, 50))
        .transform(source),
      Transformer
        .defineVia[SourceToplevel1](DestToplevel1.apply)
        .build(Field.const(_.level1.level2.int, 50))
        .transform(source)
    )(expected)
  }

  test("nested product fields can be configured by overriding the transformation that is a level above") {
    final case class SourceToplevel1(level1: SourceLevel1)
    final case class SourceLevel1(level2: SourceLevel2)
    final case class SourceLevel2(int: Int)

    final case class DestToplevel1(level1: DestLevel1)
    final case class DestLevel1(level2: DestLevel2)
    final case class DestLevel2(int: Int, extra: String)

    val source = SourceToplevel1(SourceLevel1(SourceLevel2(1)))
    val expected = DestToplevel1(DestLevel1(DestLevel2(50, "CONFIGURED")))

    assertEachEquals(
      source.into[DestToplevel1].transform(Field.const(_.level1.level2, DestLevel2(50, "CONFIGURED"))),
      source.intoVia(DestToplevel1.apply).transform(Field.const(_.level1.level2, DestLevel2(50, "CONFIGURED"))),
      Transformer
        .define[SourceToplevel1, DestToplevel1]
        .build(Field.const(_.level1.level2, DestLevel2(50, "CONFIGURED")))
        .transform(source),
      Transformer
        .defineVia[SourceToplevel1](DestToplevel1.apply)
        .build(Field.const(_.level1.level2, DestLevel2(50, "CONFIGURED")))
        .transform(source)
    )(expected)
  }

  test("nested product configuration fails if the types do not line up") {
    final case class SourceToplevel1(level1: SourceLevel1)
    final case class SourceLevel1(level2: SourceLevel2)
    final case class SourceLevel2(int: Int)

    final case class DestToplevel1(level1: DestLevel1)
    final case class DestLevel1(level2: DestLevel2)
    final case class DestLevel2(int: Int, extra: String)

    val source = SourceToplevel1(SourceLevel1(SourceLevel2(1)))

    assertFailsToCompileWith {
      """
      source.into[DestToplevel1].transform(Field.const(_.level1.level2.extra, 123))
      """
    }(
      "Configuration is not valid since the provided type (scala.Int) is not a subtype of java.lang.String @ DestToplevel1.level1.level2.extra",
      "No field 'extra' found in SourceLevel2 @ DestToplevel1.level1.level2.extra"
    )
  }: @nowarn("msg=unused local definition")

  test("Field.computed works for nested fields") {
    final case class SourceToplevel1(level1: SourceLevel1)
    final case class SourceLevel1(level2: SourceLevel2)
    final case class SourceLevel2(int: Int)

    final case class DestToplevel1(level1: DestLevel1)
    final case class DestLevel1(level2: DestLevel2)
    final case class DestLevel2(int: Int, extra: String)

    val source = SourceToplevel1(SourceLevel1(SourceLevel2(1)))
    val expected = DestToplevel1(DestLevel1(DestLevel2(1, "1CONF")))

    assertEachEquals(
      source
        .into[DestToplevel1]
        .transform(
          Field.computed(_.level1.level2.extra, a => a.level1.level2.int.toString() + "CONF")
        ),
      source
        .intoVia(DestToplevel1.apply)
        .transform(Field.computed(_.level1.level2.extra, a => a.level1.level2.int.toString() + "CONF")),
      Transformer
        .define[SourceToplevel1, DestToplevel1]
        .build(Field.computed(_.level1.level2.extra, a => a.level1.level2.int.toString() + "CONF"))
        .transform(source),
      Transformer
        .defineVia[SourceToplevel1](DestToplevel1.apply)
        .build(Field.computed(_.level1.level2.extra, a => a.level1.level2.int.toString() + "CONF"))
        .transform(source)
    )(expected)
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
        .transform(
          Case.const(_.at[SourceToplevel1.Level1].level2.at[SourceLevel2.Level2].level3.at[SourceLevel3.Extra.type], DestLevel3.One)
        ),
      Transformer
        .define[SourceToplevel1, DestToplevel1]
        .build(
          Case.const(_.at[SourceToplevel1.Level1].level2.at[SourceLevel2.Level2].level3.at[SourceLevel3.Extra.type], DestLevel3.One)
        )
        .transform(source)
    )(expected)
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
        .transform(
          Case.const(_.level1.at[SourceLevel1.Level1].level2.at[SourceLevel2.Level2].level3.at[SourceLevel3.Extra.type], DestLevel3.One)
        ),
      source
        .intoVia(DestToplevel1.apply)
        .transform(
          Case.const(_.level1.at[SourceLevel1.Level1].level2.at[SourceLevel2.Level2].level3.at[SourceLevel3.Extra.type], DestLevel3.One)
        ),
      Transformer
        .define[SourceToplevel1, DestToplevel1]
        .build(
          Case.const(_.level1.at[SourceLevel1.Level1].level2.at[SourceLevel2.Level2].level3.at[SourceLevel3.Extra.type], DestLevel3.One)
        )
        .transform(source),
      Transformer
        .defineVia[SourceToplevel1](DestToplevel1.apply)
        .build(
          Case.const(_.level1.at[SourceLevel1.Level1].level2.at[SourceLevel2.Level2].level3.at[SourceLevel3.Extra.type], DestLevel3.One)
        )
        .transform(source)
    )(expected)
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
        .transform(
          Case.const(_.at[SourceToplevel1.Level1].level2.at[SourceLevel2.Level2].level3.at[SourceLevel3.One.type], DestLevel3.Two)
        ),
      Transformer
        .define[SourceToplevel1, DestToplevel1]
        .build(
          Case.const(_.at[SourceToplevel1.Level1].level2.at[SourceLevel2.Level2].level3.at[SourceLevel3.One.type], DestLevel3.Two)
        )
        .transform(source)
    )(expected)
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
        .transform(
          Case.const(_.at[SourceToplevel1.Level1].level2.at[SourceLevel2.Level2], DestLevel2.Level2(DestLevel3.One))
        ),
      Transformer
        .define[SourceToplevel1, DestToplevel1]
        .build(
          Case.const(_.at[SourceToplevel1.Level1].level2.at[SourceLevel2.Level2], DestLevel2.Level2(DestLevel3.One))
        )
        .transform(source)
    )(expected)
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
        .transform(
          Case.const(_.at[SourceToplevel1.Level1].level2.at[SourceLevel2.Level2].level3.at[SourceLevel3.Extra.type], 123)
        )
      """
    }(
      "No child named 'Extra' found in DestLevel3 @ SourceToplevel1.at[SourceToplevel1.Level1].level2.at[SourceLevel2.Level2].level3.at[SourceLevel3.Extra.type]",
      "Configuration is not valid since the provided type (123) is not a subtype of DestLevel3 @ SourceToplevel1.at[SourceToplevel1.Level1].level2.at[SourceLevel2.Level2].level3.at[SourceLevel3.Extra.type]"
    )
  }: @nowarn("msg=unused local definition")

  test("Case.computed works for nested cases") {
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
        .transform(
          Case.computed(
            _.at[SourceToplevel1.Level1].level2.at[SourceLevel2.Level2].level3.at[SourceLevel3.Extra],
            extra => DestLevel3.One(extra.int + 5)
          )
        ),
      Transformer
        .define[SourceToplevel1, DestToplevel1]
        .build(
          Case.computed(
            _.at[SourceToplevel1.Level1].level2.at[SourceLevel2.Level2].level3.at[SourceLevel3.Extra],
            extra => DestLevel3.One(extra.int + 5)
          )
        )
        .transform(source)
    )(expected)

  }
}
