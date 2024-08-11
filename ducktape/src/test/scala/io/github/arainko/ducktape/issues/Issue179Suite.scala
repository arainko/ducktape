package io.github.arainko.ducktape.issues

import io.github.arainko.ducktape.*

class Issue179Suite extends DucktapeSuite {
  private given Mode.Accumulating.Either[String, List]()

  test("configuring a field on an identity transformation works") {
    case class Person(int: Int, str: String)

    val source = Person(1, "1")
    val expected = Person(2, "1")

    assertTransformsConfigured(source, expected)(
      Field.const(_.int, 2)
    )
  }

  test("configuring a field on an identity transformation works (fallible)") {
    case class Person(int: Int, str: String)

    val source = Person(1, "1")
    val expected = Person(2, "1")

    assertTransformsFallibleConfigured(source, Right(expected))(
      Field.fallibleConst(_.int, Right(2))
    )
  }

  test("configuring a field on a nested identity transformation works") {
    case class Level1(level2: Level2)

    case class Level2(level3: Level3)

    case class Level3(int: Int)

    val source = Level1(Level2(Level3(1)))
    val expected = Level1(Level2(Level3(2)))

    assertTransformsConfigured(source, expected)(
      Field.const(_.level2.level3.int, 2)
    )
  }

  test("configuring a field on a nested identity transformation works (fallible)") {
    case class Level1(level2: Level2)

    case class Level2(level3: Level3)

    case class Level3(int: Int)

    val source = Level1(Level2(Level3(1)))
    val expected = Level1(Level2(Level3(2)))

    assertTransformsFallibleConfigured(source, Right(expected))(
      Field.fallibleConst(_.level2.level3.int, Right(2))
    )
  }

  test("configuring a field on an identity transformation going through a coproduct transformation works") {
    enum Coprod {
      case One(int: Int)
      case Two(int: Int)
    }

    val source = Coprod.One(1)
    val expected = Coprod.One(2)

    assertTransformsConfigured(source, expected)(
      Field.const(_.at[Coprod.One].int, 2)
    )
  }

  test("configuring a field on an identity transformation going through a coproduct transformation works (fallible)") {
    enum Coprod {
      case One(int: Int)
      case Two(int: Int)
    }

    val source = Coprod.One(1)
    val expected = Coprod.One(2)

    assertTransformsFallibleConfigured(source, Right(expected))(
      Field.fallibleConst(_.at[Coprod.One].int, Right(2))
    )
  }

  test(
    "configuring a field on an identity transformation going through an '.element' transformation (eg. OptionToOption) works"
  ) {
    case class Level1(level2: Level2)

    case class Level2(level3: Option[Level3])

    case class Level3(int: Int)

    val source = Level1(Level2(Some(Level3(1))))
    val expected = Level1(Level2(Some(Level3(2))))

    assertTransformsConfigured(source, expected)(
      Field.const(_.level2.level3.element.int, 2)
    )
  }

  test(
    "configuring a field on an identity transformation going through an '.element' transformation (eg. OptionToOption) works (fallible)"
  ) {
    case class Level1(level2: Level2)

    case class Level2(level3: Option[Level3])

    case class Level3(int: Int)

    val source = Level1(Level2(Some(Level3(1))))
    val expected = Level1(Level2(Some(Level3(2))))

    assertTransformsFallibleConfigured(source, Right(expected))(
      Field.fallibleConst(_.level2.level3.element.int, Right(2))
    )
  }

  test("configuring a tuple element on an identity transformation works") {
    val source = (1, 2, 3)
    val expected = (1, 3, 3)

    assertTransformsConfigured(source, expected)(
      Field.const(_.apply(1), 3)
    )
  }

  test("configuring a tuple element on an identity transformation works (fallible)") {
    val source = (1, 2, 3)
    val expected = (1, 3, 3)

    assertTransformsFallibleConfigured(source, Right(expected))(
      Field.fallibleConst(_.apply(1), Right(3))
    )
  }

  test("configuring a case on an identity transformation works") {
    enum Coprod {
      case One(int: Int)
      case Two(int: Int)
    }

    val source = Coprod.One(1)
    val expected = Coprod.Two(1)

    assertTransformsConfigured(source, expected)(
      Case.computed(_.at[Coprod.One], src => Coprod.Two(src.int))
    )
  }

  test("configuring a case on an identity transformation works (fallible)") {
    enum Coprod {
      case One(int: Int)
      case Two(int: Int)
    }

    val source = Coprod.One(1)
    val expected = Coprod.Two(1)

    assertTransformsFallibleConfigured(source, Right(expected))(
      Case.fallibleComputed(_.at[Coprod.One], src => Right(Coprod.Two(src.int)))
    )
  }
}
