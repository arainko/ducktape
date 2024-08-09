package io.github.arainko.ducktape.issues
import io.github.arainko.ducktape.*

class Issue187Suite extends DucktapeSuite {
  test("BetweenNonOptionOption works when Mode[Option] is in scope") {

    given Transformer.Fallible[Option, Int, String] = a => Some(a.toString)

    case class Source(int: Int, str: String)
    case class Dest(int: Option[String], str: Option[String])

    Mode.FailFast.option.locally {
      val source = Source(1, "str")
      val expected = Dest(Some("1"), Some("str"))

      assertTransformsFallible(
        source,
        Some(expected)
      )
      assertEachEquals(
        source.fallibleVia(Dest.apply),
        Transformer.defineVia[Source](Dest.apply).fallible.build().transform(source)
      )(Some(expected))
    }
  }

  test("BetweenOptions works when Mode[Option] is in scope") {
    given Transformer.Fallible[Option, Int, String] = a => Some(a.toString)

    case class Source(int: Option[Int])
    case class Dest(int: Option[String])

    Mode.FailFast.option.locally {
      val source = Source(Some(1))
      val expected = Dest(Some("1"))

      assertTransformsFallible(
        source,
        Some(expected)
      )
      assertEachEquals(
        source.fallibleVia(Dest.apply),
        Transformer.defineVia[Source](Dest.apply).fallible.build().transform(source)
      )(Some(expected))

    }
  }

  test("Fallible transformation for an Option works when Mode[Option] is in scope") {
    given Transformer.Fallible[Option, Int, String] = a => Some(a.toString)

    case class Source(int: Option[Int])
    case class Dest(int: String)

    Mode.FailFast.option.locally {
      val source = Source(Some(1))
      val expected = Dest("1")

      assertTransformsFallible(
        source,
        Some(expected)
      )
      assertEachEquals(
        source.fallibleVia(Dest.apply),
        Transformer.defineVia[Source](Dest.apply).fallible.build().transform(source)
      )(Some(expected))
    }
  }

  test("Option-unwrapping works") {
    case class Dest(int1: Int, int2: Int, int3: Int, int4: Int)

    Mode.FailFast.option.locally {
      val source =
        (
          Some(1),
          Some(2),
          Some(3),
          Some(4)
        )

      val expected = Dest(1, 2, 3, 4)

      assertTransformsFallible(
        source,
        Some(expected)
      )
      assertEachEquals(
        source.fallibleVia(Dest.apply),
        Transformer.defineVia[source.type](Dest.apply).fallible.build().transform(source)
      )(Some(expected))
    }
  }
}
