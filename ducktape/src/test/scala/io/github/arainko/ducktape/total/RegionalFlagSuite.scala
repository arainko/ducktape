package io.github.arainko.ducktape.total

import io.github.arainko.ducktape.*

class RegionalFlagSuite extends DucktapeSuite {
  test("dest field regional flag covers the selected case class and everything below it") {
    case class Source(int: Int, str: String, level1: SourceLevel1)
    case class SourceLevel1(INT: Int, STR: String, LEVEL2: SourceLevel2)
    case class SourceLevel2(INT: Int, STR: String)

    case class Dest(int: Int, str: String, level1: DestLevel1)
    case class DestLevel1(INT: Int, STR: String, LEVEL2: DestLevel2)
    case class DestLevel2(INT: Int, STR: String)

    val source = Source(1, "1", SourceLevel1(2, "2", SourceLevel2(3, "3")))
    val expected = Dest(1, "1", DestLevel1(2, "2", DestLevel2(3, "3")))

    assertTransformsConfigured(source, expected)(
      Field.modifyDestNames(_.toUpperCase).regional(_.level1)
    )
  }

  test("source field regional flag covers the selected case class and everything below it") {
    case class Source(int: Int, str: String, level1: SourceLevel1)
    case class SourceLevel1(INT: Int, STR: String, LEVEL2: SourceLevel2)
    case class SourceLevel2(INT: Int, STR: String)

    case class Dest(int: Int, str: String, level1: DestLevel1)
    case class DestLevel1(INT: Int, STR: String, LEVEL2: DestLevel2)
    case class DestLevel2(INT: Int, STR: String)

    val source = Source(1, "1", SourceLevel1(2, "2", SourceLevel2(3, "3")))
    val expected = Dest(1, "1", DestLevel1(2, "2", DestLevel2(3, "3")))

    assertTransformsConfigured(source, expected)(
      Field.modifySourceNames(_.toUpperCase).regional(_.level1)
    )
  }

  test("source field regional flag covers the selected subtype of an enum and everything below it") {
    enum Source {
      case One(int: Int, str: String)
      case Two(int: Int, str: String, level1: SourceLevel1)
      case Three(int: Int, str: String)
    }

    enum Dest {
      case One(int: Int, str: String)
      case Two(INT: Int, STR: String, LEVEL1: DestLevel1)
      case Three(int: Int, str: String)
    }

    case class SourceLevel1(int: Int)
    case class DestLevel1(INT: Int)

    assertTransformsConfigured(
      Source.Two(2, "2", SourceLevel1(1)),
      Dest.Two(2, "2", DestLevel1(1))
    )(
      Field.modifySourceNames(_.toUpperCase).regional(_.at[Source.Two])
    )
  }

  test("source field regional flag covers all subtypes of an enum and everything below it") {
    case class Source(int: Int, level1: SourceEnum)
    case class Dest(int: Int, level1: DestEnum)

    enum SourceEnum {
      case One(int: Int, str: String)
      case Two(int: Int, str: String, level1: SourceLevel1)
      case Three(int: Int, str: String)
    }

    enum DestEnum {
      case One(INT: Int, STR: String)
      case Two(INT: Int, STR: String, LEVEL1: DestLevel1)
      case Three(INT: Int, STR: String)
    }

    case class SourceLevel1(int: Int)
    case class DestLevel1(INT: Int)

    assertTransformsConfigured(
      Source(1, SourceEnum.Two(2, "2", SourceLevel1(3))),
      Dest(1, DestEnum.Two(2, "2", DestLevel1(3)))
    )(
      Field.modifySourceNames(_.toUpperCase).regional(_.level1)
    )
  }

  test("dest regional flag covers all subtypes of an enum and everything below it") {
    case class Source(int: Int, level1: SourceEnum)
    case class Dest(int: Int, level1: DestEnum)

    enum DestEnum {
      case One(int: Int, str: String)
      case Two(int: Int, str: String, level1: DestLevel1)
      case Three(int: Int, str: String)
    }

    enum SourceEnum {
      case One(INT: Int, STR: String)
      case Two(INT: Int, STR: String, LEVEL1: SourceLevel1)
      case Three(INT: Int, STR: String)
    }

    case class SourceLevel1(INT: Int)
    case class DestLevel1(int: Int)

    assertTransformsConfigured(
      Source(1, SourceEnum.Two(2, "2", SourceLevel1(3))),
      Dest(1, DestEnum.Two(2, "2", DestLevel1(3)))
    )(
      Field.modifyDestNames(_.toUpperCase).regional(_.level1)
    )
  }

  test("source field regional flag covers all subtypes of an enum and everything below (even when the enum is nested)") {
    case class Source(int: Int, level1: SourceEnum)
    case class Dest(int: Int, level1: DestEnum)

    sealed trait SourceEnum

    object SourceEnum {
      sealed trait NestLevel1 extends SourceEnum
      sealed trait NestLevel2 extends SourceEnum

      case class One(int: Int, str: String) extends NestLevel2
      case class Two(int: Int, str: String, level1: SourceLevel1) extends NestLevel1
      case class Three(int: Int, str: String) extends NestLevel2
    }

    sealed trait DestEnum

    object DestEnum {
      sealed trait NestLevel1 extends DestEnum
      sealed trait NestLevel2 extends DestEnum

      case class One(INT: Int, STR: String) extends NestLevel2
      case class Two(INT: Int, STR: String, LEVEL1: DestLevel1) extends NestLevel1
      case class Three(INT: Int, STR: String) extends NestLevel2
    }

    case class SourceLevel1(int: Int)
    case class DestLevel1(INT: Int)

    assertTransformsConfigured(
      Source(1, SourceEnum.Two(2, "2", SourceLevel1(3))),
      Dest(1, DestEnum.Two(2, "2", DestLevel1(3)))
    )(
      Field.modifySourceNames(_.toUpperCase).regional(_.level1)
    )
  }

  test("dest field regional flag covers all subtypes of an enum and everything below (even when the enum is nested)") {
    case class Source(int: Int, level1: SourceEnum)
    case class Dest(int: Int, level1: DestEnum)

    sealed trait SourceEnum

    object SourceEnum {
      sealed trait NestLevel1 extends SourceEnum
      sealed trait NestLevel2 extends SourceEnum

      case class One(INT: Int, STR: String) extends NestLevel2
      case class Two(INT: Int, STR: String, LEVEL1: SourceLevel1) extends NestLevel1
      case class Three(INT: Int, STR: String) extends NestLevel2
    }

    sealed trait DestEnum

    object DestEnum {
      sealed trait NestLevel1 extends DestEnum
      sealed trait NestLevel2 extends DestEnum

      case class One(int: Int, str: String) extends NestLevel2
      case class Two(int: Int, str: String, level1: DestLevel1) extends NestLevel1
      case class Three(int: Int, str: String) extends NestLevel2
    }

    case class SourceLevel1(INT: Int)
    case class DestLevel1(int: Int)

    assertTransformsConfigured(
      Source(1, SourceEnum.Two(2, "2", SourceLevel1(3))),
      Dest(1, DestEnum.Two(2, "2", DestLevel1(3)))
    )(
      Field.modifyDestNames(_.toUpperCase).regional(_.level1)
    )
  }

  test(
    "dest field regional flag covers all subtypes of an enum and everything below (even when the enum is nested, and we pick one of the sub-enums)"
  ) {
    case class Source(int: Int, level1: SourceEnum)
    case class Dest(int: Int, level1: DestEnum)

    sealed trait SourceEnum

    object SourceEnum {
      sealed trait NestLevel1 extends SourceEnum
      sealed trait NestLevel2 extends SourceEnum

      case class One(int: Int, str: String) extends NestLevel2
      case class Two(INT: Int, STR: String, LEVEL1: SourceLevel1) extends NestLevel1
      case class Three(int: Int, str: String) extends NestLevel2
    }

    sealed trait DestEnum

    object DestEnum {
      sealed trait NestLevel1 extends DestEnum
      sealed trait NestLevel2 extends DestEnum

      case class One(int: Int, str: String) extends NestLevel2
      case class Two(int: Int, str: String, level1: DestLevel1) extends NestLevel1
      case class Three(int: Int, str: String) extends NestLevel2
    }

    case class SourceLevel1(INT: Int)
    case class DestLevel1(int: Int)

    assertTransformsConfigured(
      Source(1, SourceEnum.Two(2, "2", SourceLevel1(3))),
      Dest(1, DestEnum.Two(2, "2", DestLevel1(3)))
    )(
      Field.modifyDestNames(_.toUpperCase).regional(_.level1.at[DestEnum.NestLevel1])
    )
  }

  test(
    "source field regional flag covers all subtypes of an enum and everything below (even when the enum is nested, and we pick one of the sub-enums)"
  ) {
    case class Source(int: Int, level1: SourceEnum)
    case class Dest(int: Int, level1: DestEnum)

    sealed trait SourceEnum

    object SourceEnum {
      sealed trait NestLevel1 extends SourceEnum
      sealed trait NestLevel2 extends SourceEnum

      case class One(int: Int, str: String) extends NestLevel2
      case class Two(int: Int, str: String, level1: SourceLevel1) extends NestLevel1
      case class Three(int: Int, str: String) extends NestLevel2
    }

    sealed trait DestEnum

    object DestEnum {
      sealed trait NestLevel1 extends DestEnum
      sealed trait NestLevel2 extends DestEnum

      case class One(int: Int, str: String) extends NestLevel2
      case class Two(INT: Int, STR: String, LEVEL1: DestLevel1) extends NestLevel1
      case class Three(int: Int, str: String) extends NestLevel2
    }

    case class SourceLevel1(int: Int)
    case class DestLevel1(INT: Int)

    assertTransformsConfigured(
      Source(1, SourceEnum.Two(2, "2", SourceLevel1(3))),
      Dest(1, DestEnum.Two(2, "2", DestLevel1(3)))
    )(
      Field.modifySourceNames(_.toUpperCase).regional(_.level1.at[SourceEnum.NestLevel1])
    )
  }

  test("source case regional flag covers the selected subtype and everything below (picked as a field in case class)") {
    case class Source(int: Int, level1: SourceEnum)
    case class Dest(int: Int, level1: DestEnum)

    enum DestEnum {
      case one(int: Int, str: String)
      case two(int: Int, str: String, level1: DestLevel1, level2: DestLevel1Enum)
      case three(int: Int, str: String)
    }

    enum SourceEnum {
      case ONE(int: Int, str: String)
      case TWO(int: Int, str: String, level1: SourceLevel1, level2: SourceLevel1Enum)
      case THREE(int: Int, str: String)
    }

    enum SourceLevel1Enum {
      case One
      case Two
    }

    enum DestLevel1Enum {
      case one
      case two
    }

    case class SourceLevel1(int: Int)
    case class DestLevel1(int: Int)

    assertTransformsConfigured(
      Source(1, SourceEnum.TWO(2, "2", SourceLevel1(3), SourceLevel1Enum.Two)),
      Dest(1, DestEnum.two(2, "2", DestLevel1(3), DestLevel1Enum.two))
    )(
      Case.modifySourceNames(_.toLowerCase).regional(_.level1)
    )
  }

  test("source case regional flag DOESN'T cover the selected subtype (picked as a subtype with .at)") {
    case class Source(int: Int, level1: SourceEnum)
    case class Dest(int: Int, level1: DestEnum)

    enum DestEnum {
      case One(int: Int, str: String)
      case Two(int: Int, str: String, level1: DestLevel1, level2: DestLevel1Enum)
      case Three(int: Int, str: String)
    }

    enum SourceEnum {
      case One(int: Int, str: String)
      case Two(int: Int, str: String, level1: SourceLevel1, level2: SourceLevel1Enum)
      case Three(int: Int, str: String)
    }

    enum SourceLevel1Enum {
      case One
      case Two
    }

    enum DestLevel1Enum {
      case one
      case two
    }

    case class SourceLevel1(int: Int)
    case class DestLevel1(int: Int)

    assertTransformsConfigured(
      Source(1, SourceEnum.Two(2, "2", SourceLevel1(3), SourceLevel1Enum.Two)),
      Dest(1, DestEnum.Two(2, "2", DestLevel1(3), DestLevel1Enum.two))
    )(
      Case.modifySourceNames(_.toLowerCase).regional(_.level1.at[SourceEnum.Two])
    )
  }

  test("dest case regional flag DOESN'T cover the selected subtype (picked as a subtype with .at)") {
    case class Source(int: Int, level1: SourceEnum)
    case class Dest(int: Int, level1: DestEnum)

    enum DestEnum {
      case One(int: Int, str: String)
      case Two(int: Int, str: String, level1: DestLevel1, level2: DestLevel1Enum)
      case Three(int: Int, str: String)
    }

    enum SourceEnum {
      case One(int: Int, str: String)
      case Two(int: Int, str: String, level1: SourceLevel1, level2: SourceLevel1Enum)
      case Three(int: Int, str: String)
    }

    enum SourceLevel1Enum {
      case one
      case two
    }

    enum DestLevel1Enum {
      case One
      case Two
    }

    case class SourceLevel1(int: Int)
    case class DestLevel1(int: Int)

    assertTransformsConfigured(
      Source(1, SourceEnum.Two(2, "2", SourceLevel1(3), SourceLevel1Enum.two)),
      Dest(1, DestEnum.Two(2, "2", DestLevel1(3), DestLevel1Enum.Two))
    )(
      Case.modifyDestNames(_.toLowerCase).regional(_.level1.at[DestEnum.Two])
    )
  }

}
