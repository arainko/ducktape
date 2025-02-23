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


}
