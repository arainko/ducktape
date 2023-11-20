package io.github.arainko.ducktape.total

import io.github.arainko.ducktape.*

import scala.annotation.nowarn

class ErrorReportingSuite extends DucktapeSuite {
  test("errors for all missing fields are reported at once") {
    final case class SourceToplevel1(level1: SourceLevel1)
    final case class SourceLevel1(level2: SourceLevel2)
    final case class SourceLevel2(int: Int)

    final case class DestToplevel1(level1: DestLevel1)
    final case class DestLevel1(level2: DestLevel2, level1Extra: Int, level2Extra: String)
    final case class DestLevel2(int: Int, level3extra: String)

    def source: SourceToplevel1 = ???

    assertFailsToCompileWith {
      """
      source.to[DestToplevel1]
      """
    } {
      """No field 'level2Extra' found in SourceLevel1 @ DestToplevel1.level1.level2Extra
      |No field 'level1Extra' found in SourceLevel1 @ DestToplevel1.level1.level1Extra
      |No field 'level3extra' found in SourceLevel2 @ DestToplevel1.level1.level2.level3extra""".stripMargin
    }
  }: @nowarn("msg=unused local definition")

  test("errors for all missing coproduct cases are reported at once") {
    enum SourceToplevel1 {
      case Level1(level2: SourceLevel2)
      case Level1Extra1
      case Level1Extra2
    }

    enum SourceLevel2 {
      case Level2(level3: SourceLevel3)
      case Level2Extra1
      case Level2Extra2
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

    def source: SourceToplevel1 = ???

    assertFailsToCompileWith {
      """
      source.to[DestToplevel1]
      """
    } {
      """No child named 'Level1Extra2' found in DestToplevel1 @ SourceToplevel1.at[SourceToplevel1.Level1Extra2]
      |No child named 'Level1Extra1' found in DestToplevel1 @ SourceToplevel1.at[SourceToplevel1.Level1Extra1]
      |No child named 'Level2Extra2' found in DestLevel2 @ SourceToplevel1.at[SourceToplevel1.Level1].level2.at[SourceLevel2.Level2Extra2]
      |No child named 'Level2Extra1' found in DestLevel2 @ SourceToplevel1.at[SourceToplevel1.Level1].level2.at[SourceLevel2.Level2Extra1]
      |No child named 'Extra' found in DestLevel3 @ SourceToplevel1.at[SourceToplevel1.Level1].level2.at[SourceLevel2.Level2].level3.at[SourceLevel3.Extra]""".stripMargin
    }
  }: @nowarn("msg=unused local definition")

  test("product configurations should fail when using a path component that doesn't belong to the actual transformation path") {
    final case class SourceToplevel1(level1: SourceLevel1)
    final case class SourceLevel1(level2: SourceLevel2)
    final case class SourceLevel2(int: Int)

    final case class DestToplevel1(level1: DestLevel1)
    final case class DestLevel1(level2: DestLevel2)
    final case class DestLevel2(int: Int, level3extra: String)

    def source: SourceToplevel1 = ???

    assertFailsToCompileWith {
      """
      source.into[DestToplevel1].transform(Field.const(_.level1.level2.int.toByte.toByte.toByte, 1.toByte))
      """
    }(
      "The path segment 'toByte' is not valid as it is not a field of a case class or an argument of a function @ DestToplevel1.level1.level2.int",
      "No field 'level3extra' found in SourceLevel2 @ DestToplevel1.level1.level2.level3extra"
    )
  }: @nowarn("msg=unused local definition")

  test("coproduct configurations fail when using a case component that isn't a subtype") {
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

    def source: SourceToplevel1 = ???

    assertFailsToCompileWith {
      """
      source.into[DestToplevel1].transform(Case.const(_.at[SourceToplevel1.Level1].level2.at[SourceLevel2], 123))
      """
    }(
      "No child named 'Extra' found in DestLevel3 @ SourceToplevel1.at[SourceToplevel1.Level1].level2.at[SourceLevel2.Level2].level3.at[SourceLevel3.Extra]",
      "'at[SourceLevel2]' is not a valid case accessor @ SourceToplevel1.at[SourceToplevel1.Level1].level2"
    )
  }: @nowarn("msg=unused local definition")

  test("erroneous config doesn't overshadow errors that lie in its subpaths") {
    final case class SourceToplevel1(level1: SourceLevel1)
    final case class SourceLevel1(level2: SourceLevel2)
    final case class SourceLevel2(int: Int)

    final case class DestToplevel1(level1: DestLevel1)
    final case class DestLevel1(level2: DestLevel2, level2Extra: String)
    final case class DestLevel2(int: Int, level3extra: String)

    def source: SourceToplevel1 = ???

    assertFailsToCompileWith {
      """
      source.into[DestToplevel1].transform(Field.default(_.level1))
      """
    }(
      """No field 'level2Extra' found in SourceLevel1 @ DestToplevel1.level1.level2Extra
      |No field 'level3extra' found in SourceLevel2 @ DestToplevel1.level1.level2.level3extra""".stripMargin,
      "The field 'level1' doesn't have a default value @ DestToplevel1.level1"
    )
  }: @nowarn("msg=unused local definition")

  test("erroneous configs on plan nodes that produce an error show the suppressed error") {
    final case class SourceToplevel1()

    final case class DestToplevel1(level2Extra: Int)

    def source: SourceToplevel1 = ???

    assertFailsToCompileWith {
      """
      source.into[DestToplevel1].transform(
        Field.default(_.level2Extra),
        Field.default(_.level2Extra.toByte)
      )
      """
    }(
      """The path segment 'toByte' is not valid as it is not a field of a case class or an argument of a function @ DestToplevel1
      |  SUPPRESSES: The field 'level2Extra' doesn't have a default value @ DestToplevel1.level2Extra""".stripMargin,
      "The field 'level2Extra' doesn't have a default value @ DestToplevel1.level2Extra",
      "No field 'level2Extra' found in SourceToplevel1 @ DestToplevel1.level2Extra"
    )
  }: @nowarn("msg=unused local definition")

  test("recursive transformations are detected") {
    final case class Rec[A](value: Int, rec: Option[Rec[A]])

    def source: Rec[Int] = ???

    assertFailsToCompileWith {
      """
      source.to[Rec[Int | String]]
      """
    }("Recursive type suspected, consider using Transformer.define or Transformer.defineVia instead @ Rec[Int | String]" + (".rec" * 32))
  }: @nowarn("msg=unused local definition")
}
