package io.github.arainko.ducktape.total

import io.github.arainko.ducktape.*

class FlagSuite extends DucktapeSuite {
  test("dest field renames work") {
    case class Source(int: Int, str: String)
    case class Dest(INT_ADDITION: Int, STR: String)

    assertTransformsConfigured(
      Source(1, "asd"),
      Dest(1, "asd")
    )(
      Field.modifyDestNames(_.toLowerCase.replace("_addition", ""))
    )
  }

  test("source field renames work") {
    case class Source(int: Int, str: String)
    case class Dest(INT_ADDITION: Int, STR: String)

    assertTransformsConfigured(
      Source(1, "asd"),
      Dest(1, "asd")
    )(
      Field.modifySourceNames(_.toUpperCase.rename("INT", "INT_ADDITION")),
    )
  }

  test("source case renames work") {
    enum Source {
      case Case1
      case Case2
      case Case3(int: Int, str: String)
    }

    enum Dest {
      case CASE1
      case case2
      case CASE3_WITH_A_BONUS(int: Int, str: String)
    }

    assertTransformsConfigured(
      Source.Case1,
      Dest.CASE1
    )(
      Case.modifySourceNames(_.toLowerCase),
      Case.modifyDestNames(_.replace("_WITH_A_BONUS", "").toLowerCase)
    )
  }

  test("dest case renames work") {
    enum Source {
      case Case1
      case Case2
      case Case3(int: Int, str: String)
    }

    enum Dest {
      case CASE1
      case case2
      case CASE3_WITH_A_BONUS(int: Int, str: String)
    }

    assertTransformsConfigured(
      Source.Case1,
      Dest.CASE1
    )(
      Case.modifySourceNames(_.toLowerCase),
      Case.modifyDestNames(_.toLowerCase.replace("_with_a_bonus", ""))
    )
  }

  test("source case object renames work") {
    case object Source_Frank_Beltrame
    case object SOURCE

    assertTransformsConfigured(Source_Frank_Beltrame, SOURCE)(
      Case.modifySourceNames(_.replace("_Frank_Beltrame", "").toUpperCase)
    )
  }

  test("dest case object renames work") {
    case object Source_Frank_Beltrame
    case object SOURCE

    assertTransformsConfigured(SOURCE, Source_Frank_Beltrame)(
      Case.modifyDestNames(_.replace("_Frank_Beltrame", "").toUpperCase)
    )
  }

  test("dest local flag covers the selected case class and nothing else") {
    case class Source(int: Int, str: String, level1: SourceLevel1)
    case class SourceLevel1(INT: Int, STR: String, LEVEL2: SourceLevel2)
    case class SourceLevel2(int: Int, str: String)

    case class Dest(int: Int, str: String, level1: DestLevel1)
    case class DestLevel1(int: Int, str: String, level2: DestLevel2)
    case class DestLevel2(int: Int, str: String)

    val source = Source(1, "1", SourceLevel1(2, "2", SourceLevel2(3, "3")))
    val expected = Dest(1, "1", DestLevel1(2, "2", DestLevel2(3, "3")))

    assertTransformsConfigured(source, expected)(
      Field.modifyDestNames(_.toUpperCase).local(_.level1)
    )
  }

  test("source local flag covers the selected case class and nothing else") {
    case class Source(int: Int, str: String, level1: SourceLevel1)
    case class SourceLevel1(int: Int, str: String, level2: SourceLevel2)
    case class SourceLevel2(int: Int, str: String)

    case class Dest(int: Int, str: String, level1: DestLevel1)
    case class DestLevel1(INT: Int, STR: String, LEVEL2: DestLevel2)
    case class DestLevel2(int: Int, str: String)

    val source = Source(1, "1", SourceLevel1(2, "2", SourceLevel2(3, "3")))
    val expected = Dest(1, "1", DestLevel1(2, "2", DestLevel2(3, "3")))

    assertTransformsConfigured(source, expected)(
      Field.modifySourceNames(_.toUpperCase).local(_.level1)
    )
  }

  test("source local flag covers the selected subtype of an enum and nothing else") {
    case class SourceLevel1(int: Int)
    case class DestLevel1(int: Int)

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

    assertTransformsConfigured(
      Source.Two(2, "2", SourceLevel1(1)),
      Dest.Two(2, "2", DestLevel1(1))
    )(
      Field.modifySourceNames(_.toUpperCase).local(_.at[Source.Two])
    )
  }

  test("source local flag covers all subtypes of an enum and nothing else") {
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
    case class DestLevel1(int: Int)

    assertTransformsConfigured(
      Source(1,SourceEnum.Two(2, "2", SourceLevel1(3))),
      Dest(1, DestEnum.Two(2, "2", DestLevel1(3)))
    )(
      Field.modifySourceNames(_.toUpperCase).local(_.level1)
    )
  }

  test("source local flag covers all subtypes of an enum and nothing else") {
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

    case class SourceLevel1(int: Int)
    case class DestLevel1(int: Int)

    assertTransformsConfigured(
      Source(1,SourceEnum.Two(2, "2", SourceLevel1(3))),
      Dest(1, DestEnum.Two(2, "2", DestLevel1(3)))
    )(
      Field.modifyDestNames(_.toUpperCase).local(_.level1)
    )
  }


}
