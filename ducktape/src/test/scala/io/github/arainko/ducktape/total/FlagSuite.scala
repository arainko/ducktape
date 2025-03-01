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

  test("source function arg renames work") {
    case class Source(int: Int, str: String)
    case class Dest(INT_ADDITION: Int, STR: String)

    val source = Source(1, "1")
    val expected = Dest(1, "1")

    assertEachEquals(
      source.intoVia(Dest.apply).transform(
        Field.modifySourceNames(_.toUpperCase.rename("INT", "INT_ADDITION"))
      ),
      Transformer.defineVia[Source](Dest.apply).build(
        Field.modifySourceNames(_.toUpperCase.rename("INT", "INT_ADDITION"))
      ).transform(source)
    )(expected)
  }

  test("dest function arg renames work") {
    case class Source(int: Int, str: String)
    case class Dest(INT_ADDITION: Int, STR: String)

    val source = Source(1, "1")
    val expected = Dest(1, "1")

    assertEachEquals(
      source.intoVia(Dest.apply).transform(
        Field.modifyDestNames(_.toLowerCase.replace("_addition", ""))
      ),
      Transformer.defineVia[Source](Dest.apply).build(
        Field.modifyDestNames(_.toLowerCase.replace("_addition", ""))
      ).transform(source)
    )(expected)
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

  test("dest flag carries through BetweenOptions") {
    case class Source(int: Int, str: String, level1: Option[Source.Level1])
    object Source {
      case class Level1(int: Int, level2: Level2)
      case class Level2(str: String)
    }


    case class Dest(int: Int, str: String, level1: Option[Dest.Level1])
    object Dest {
      case class Level1(int: Int, level2: Level2)
      case class Level2(STR: String)
    }

    assertTransformsConfigured(
      Source(1, "1", Some(Source.Level1(2, Source.Level2("3")))),
      Dest(1, "1", Some(Dest.Level1(2, Dest.Level2("3"))))
    )(
      Field.modifyDestNames(_.toLowerCase).local(_.level1.element.level2),
    )
  }

  test("source flag carries through BetweenOptions") {
    case class Source(int: Int, str: String, level1: Option[Source.Level1])
    object Source {
      case class Level1(int: Int, level2: Level2)
      case class Level2(STR: String)
    }


    case class Dest(int: Int, str: String, level1: Option[Dest.Level1])
    object Dest {
      case class Level1(int: Int, level2: Level2)
      case class Level2(str: String)
    }

    assertTransformsConfigured(
      Source(1, "1", Some(Source.Level1(2, Source.Level2("3")))),
      Dest(1, "1", Some(Dest.Level1(2, Dest.Level2("3"))))
    )(
      Field.modifySourceNames(_.toLowerCase).local(_.level1.element.level2),
    )
  }

  test("dest flag carries through NonOptionOption") {
    case class Source(int: Int, str: String, level1: Source.Level1)
    object Source {
      case class Level1(int: Int, level2: Level2)
      case class Level2(str: String)
    }


    case class Dest(int: Int, str: String, level1: Option[Dest.Level1])
    object Dest {
      case class Level1(int: Int, level2: Level2)
      case class Level2(STR: String)
    }

    assertTransformsConfigured(
      Source(1, "1", Source.Level1(2, Source.Level2("3"))),
      Dest(1, "1", Some(Dest.Level1(2, Dest.Level2("3"))))
    )(
      Field.modifyDestNames(_.toLowerCase).local(_.level1.element.level2),
    )
  }

  test("source flag carries through NonOptionOption") {
    case class Source(int: Int, str: String, level1: Source.Level1)
    object Source {
      case class Level1(int: Int, level2: Level2)
      case class Level2(STR: String)
    }


    case class Dest(int: Int, str: String, level1: Option[Dest.Level1])
    object Dest {
      case class Level1(int: Int, level2: Level2)
      case class Level2(str: String)
    }

    assertTransformsConfigured(
      Source(1, "1", Source.Level1(2, Source.Level2("3"))),
      Dest(1, "1", Some(Dest.Level1(2, Dest.Level2("3"))))
    )(
      Field.modifySourceNames(_.toLowerCase).local(_.level1.level2),
    )
  }

  test("dest flag carries through BetweenCollections") {
    case class Source(int: Int, str: String, level1: List[Source.Level1])
    object Source {
      case class Level1(int: Int, level2: Level2)
      case class Level2(str: String)
    }


    case class Dest(int: Int, str: String, level1: Vector[Dest.Level1])
    object Dest {
      case class Level1(int: Int, level2: Level2)
      case class Level2(STR: String)
    }

    assertTransformsConfigured(
      Source(1, "1", List(Source.Level1(2, Source.Level2("3")))),
      Dest(1, "1", Vector(Dest.Level1(2, Dest.Level2("3"))))
    )(
      Field.modifyDestNames(_.toLowerCase).local(_.level1.element.level2),
    )
  }

  test("source flag carries through BetweenCollections") {
    case class Source(int: Int, str: String, level1: List[Source.Level1])
    object Source {
      case class Level1(int: Int, level2: Level2)
      case class Level2(STR: String)
    }

    case class Dest(int: Int, str: String, level1: Vector[Dest.Level1])
    object Dest {
      case class Level1(int: Int, level2: Level2)
      case class Level2(str: String)
    }

    assertTransformsConfigured(
      Source(1, "1", List(Source.Level1(2, Source.Level2("3")))),
      Dest(1, "1", Vector(Dest.Level1(2, Dest.Level2("3"))))
    )(
      Field.modifySourceNames(_.toLowerCase).local(_.level1.element.level2),
    )
  }

  test("dest flag carries through ProductTuple") {
    case class Source(int: Int, str: String, level1: List[Source.Level1])
    object Source {
      case class Level1(int: Int, level2: Level2)
      case class Level2(str: String)
    }


    case class Dest(int: Int, str: String, level1: Vector[(Int, Dest.Level2)])
    object Dest {
      case class Level2(STR: String)
    }

    assertTransformsConfigured(
      Source(1, "1", List(Source.Level1(2, Source.Level2("3")))),
      Dest(1, "1", Vector((2, Dest.Level2("3"))))
    )(
      Field.modifyDestNames(_.toLowerCase).local(_.level1.element._2),
    )
  }

  test("source flag carries through ProductTuple") {
    case class Source(int: Int, str: String, level1: List[Source.Level1])
    object Source {
      case class Level1(int: Int, level2: Level2)
      case class Level2(STR: String)
    }


    case class Dest(int: Int, str: String, level1: Vector[(Int, Dest.Level2)])
    object Dest {
      case class Level2(str: String)
    }

    assertTransformsConfigured(
      Source(1, "1", List(Source.Level1(2, Source.Level2("3")))),
      Dest(1, "1", Vector((2, Dest.Level2("3"))))
    )(
      Field.modifySourceNames(_.toLowerCase).local(_.level1.element.level2),
    )
  }

  test("source flag carries through TupleProduct") {
    case class Source(int: Int, str: String, level1: List[(Int, Source.Level2)])
    object Source {
      case class Level2(str: String)
    }

    case class Dest(int: Int, str: String, level1: Vector[Dest.Level1])
    object Dest {
      case class Level1(int: Int, level2: Level2)
      case class Level2(STR: String)
    }

    assertTransformsConfigured(
      Source(1, "1", List((2, Source.Level2("3")))),
      Dest(1, "1", Vector(Dest.Level1(2, Dest.Level2("3"))))
    )(
      Field.modifySourceNames(_.toUpperCase).local(_.level1.element._2),
    )
  }

  test("dest flag carries through TupleProduct") {
    case class Source(int: Int, str: String, level1: List[(Int, Source.Level2)])
    object Source {
      case class Level2(str: String)
    }

    case class Dest(int: Int, str: String, level1: Vector[Dest.Level1])
    object Dest {
      case class Level1(int: Int, level2: Level2)
      case class Level2(STR: String)
    }

    assertTransformsConfigured(
      Source(1, "1", List((2, Source.Level2("3")))),
      Dest(1, "1", Vector(Dest.Level1(2, Dest.Level2("3"))))
    )(
      Field.modifyDestNames(_.toLowerCase).local(_.level1.element.level2),
    )
  }

  test("source flag carries through BetweenTuples") {
    case class Source(int: Int, str: String, level1: List[(Int, Source.Level2)])
    object Source {
      case class Level2(str: String)
    }

    case class Dest(int: Int, str: String, level1: Vector[(Int, Dest.Level2)])
    object Dest {
      case class Level2(STR: String)
    }

    assertTransformsConfigured(
      Source(1, "1", List((2, Source.Level2("3")))),
      Dest(1, "1", Vector((2, Dest.Level2("3"))))
    )(
      Field.modifySourceNames(_.toUpperCase).local(_.level1.element._2),
    )
  }

  test("dest flag carries through BetweenTuples") {
    case class Source(int: Int, str: String, level1: List[(Int, Source.Level2)])
    object Source {
      case class Level2(str: String)
    }

    case class Dest(int: Int, str: String, level1: Vector[Option[(Int, Dest.Level2)]])
    object Dest {
      case class Level2(STR: String)
    }

    assertTransformsConfigured(
      Source(1, "1", List((2, Source.Level2("3")))),
      Dest(1, "1", Vector(Some((2, Dest.Level2("3")))))
    )(
      Field.modifyDestNames(_.toLowerCase).local(_.level1.element.element._2),
    )
  }

  test("source flag carries through BetweenFallibleNonFallible") {
    case class Source(int: Int, str: String, level1: Option[Source.Level1])
    object Source {
      case class Level1(int: Int, level2: Level2)
      case class Level2(STR: String)
    }

    case class Dest(int: Int, str: String, level1: Dest.Level1)
    object Dest {
      case class Level1(int: Int, level2: Level2)
      case class Level2(str: String)
    }

    Mode.FailFast.option.locally {
      assertTransformsFallibleConfigured(
        Source(1, "1", Some(Source.Level1(2, Source.Level2("3")))),
        Some(Dest(1, "1", Dest.Level1(2, Dest.Level2("3")))),
      )(
        Field.modifySourceNames(_.toLowerCase).local(_.level1.element.level2),
      )
    }
  }

  //TODO: Broken! - however rethink BetweenFallibleNonFallible and BetweenFallibles...
  // test("dest flag carries through BetweenFallibleNonFallible") {
  //   case class Source(int: Int, str: String, level1: Option[Source.Level1])
  //   object Source {
  //     case class Level1(int: Int, level2: Level2)
  //     case class Level2(STR: String)
  //   }

  //   case class Dest(int: Int, str: String, level1: Dest.Level1)
  //   object Dest {
  //     case class Level1(int: Int, level2: Level2)
  //     case class Level2(str: String)
  //   }

  //   Mode.FailFast.option.locally {
  //     assertTransformsFallibleConfigured(
  //       Source(1, "1", Some(Source.Level1(2, Source.Level2("3")))),
  //       Some(Dest(1, "1", Dest.Level1(2, Dest.Level2("3")))),
  //     )(
  //       Field.modifyDestNames(_.toUpperCase).regional(_.level1.level2),
  //     )
  //   }
  // }

  // test("source flag carries through BetweenFallibleNonFallible") {
  //   case class Source(int: Int, str: String, level1: Either[String, Source.Level1])
  //   object Source {
  //     case class Level1(int: Int, level2: Level2)
  //     case class Level2(STR: String)
  //   }

  //   case class Dest(int: Int, str: String, level1: Either[String, Dest.Level1])
  //   object Dest {
  //     case class Level1(int: Int, level2: Level2)
  //     case class Level2(str: String)
  //   }

  //   Mode.FailFast.either[String].locally {
  //     assertTransformsFallibleConfigured(
  //       Source(1, "1", Right(Source.Level1(2, Source.Level2("3")))),
  //       Right(Dest(1, "1", Right(Dest.Level1(2, Dest.Level2("3"))))),
  //     )(
  //       Field.modifySourceNames(_.toLowerCase).local(_.level1.element.level2),
  //     )
  //   }
  // }

  //todo: tuple-function, fallibles
  //todo: priority overwrites of flags
  //todo: name ambiguities
  //todo: regional flags (copy-paste of local flag tests)
  //todo: type specific flags
}
