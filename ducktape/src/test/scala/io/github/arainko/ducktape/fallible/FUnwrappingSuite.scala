package io.github.arainko.ducktape.fallible

import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.fallible.model.Positive
import scala.annotation.unused

class FUnwrappingSuite extends DucktapeSuite {

  test("F-unwrapping works with match type syntax") {
    val source =
      (
        Right(1),
        Right(2),
        Right(3),
        Right(4)
      )

    Mode.Accumulating.either[String, List].locally {
      val expected = Right((1, 2, 3, 4))
      val actual = source.fallibleTo[Tuple.InverseMap[source.type, Mode.current.Self]]
      assertEquals(actual, expected)
    }

  }

  test("F-unwrapping with a non-fallible transformation underneath works") {
    val source =
      (
        Right(1),
        Right(2),
        Right(3),
        Right(4)
      )

    Mode.Accumulating.either[String, List].locally {
      val expected = Right((1, 2, 3, 4))

      assertTransformsFallible(source, expected)
    }
  }

  test("F-unwrapping with a fallible transformation underneath works") {
    val source =
      (
        Right(1),
        Right(2),
        Right(3),
        Right(4)
      )

    Mode.FailFast.either[String].locally {
      val expected = Right((Positive(1), Positive(2), Positive(3), Positive(4)))

      assertTransformsFallible(source, expected)
    }
  }

  test(
    "F-unwrapping with a fallible transformation underneath works for Mode.Accumulating when a Mode.FailFast is also in scope"
  ) {
    val source =
      (
        Right(1),
        Right(2),
        Right(3),
        Right(4)
      )

    Mode.FailFast.either[List[String]].locally {
      Mode.Accumulating.either[String, List].locally {
        val expected = Right((Positive(1), Positive(2), Positive(3), Positive(4)))

        assertTransformsFallible(source, expected)
      }
    }
  }

  test("F-unwrapped transformations with a non-fallible transformation underneath can be configured") {
    enum Source {
      case One
      case Two
    }

    enum Dest {
      case One
      case Three
    }

    val source =
      (
        Right(Source.One),
        Right((Source.One, Source.Two))
      )

    case class DestToplevel(field1: Dest, field2: (Dest, Dest))

    Mode.Accumulating.either[String, List].locally {
      val expected = DestToplevel(Dest.One, (Dest.One, Dest.Three))

      assertTransformsFallibleConfigured(
        source,
        Right(expected)
      )(
        Field.const(_.field2.apply(0), Dest.One),
        Field.const(_.field2.apply(1), Dest.Three),
        Case.const(_.apply(0).element.at[Source.Two.type], Dest.Three)
      )
    }

  }

  test("F-unwrapped transformations with a non-fallible transformation underneath cannot be configured with fallible configs") {
    enum Source {
      case One
      case Two
    }

    enum Dest {
      case One
      case Three
    }

    @unused val source =
      Tuple1(Right(Source.One))

    @unused case class DestToplevel(field1: Dest)

    Mode.Accumulating.either[String, List].locally {

      assertFailsToCompileWith {
        """
        Mode.Accumulating.either[String, List].locally {
          source.into[DestToplevel].fallible.transform(
            Case.fallibleConst(_.apply(0).element.at[Source.Two.type], Left(List("welp"))),
          )
        }
        """
      }(
        """Fallible configuration is not supported for F-unwrapped transformations with Mode.Accumulating.
You can make this work if you supply a deprioritized instance of Mode.FailFast for the same wrapper type. @ Tuple1[Right[Nothing, Source]].apply(0)""",
        "No child named 'Two' found in Dest @ Tuple1[Right[Nothing, Source]].apply(0).element.at[Source.Two.type]"
      )
    }
  }

  test("F-unwrapped transformations with a fallible transformation underneath can be configured") {
    enum Source {
      case One
      case Two
    }

    enum Dest {
      case One
      case Three
    }

    val source =
      (
        Right(Source.One),
        Right((Source.One, Source.Two))
      )

    case class DestToplevel(field1: Dest, field2: (Dest, Dest))

    Mode.FailFast.either[List[String]].locally {
      Mode.Accumulating.either[String, List].locally {
        val expected = DestToplevel(Dest.One, (Dest.One, Dest.Three))

        assertTransformsFallibleConfigured(
          source,
          Right(expected)
        )(
          Field.fallibleConst(_.field2.apply(0), Right(Dest.One)),
          Field.fallibleConst(_.field2.apply(1), Right(Dest.Three)),
          Case.fallibleConst(_.apply(0).element.at[Source.Two.type], Right(Dest.Three))
        )
      }
    }
  }
}
