package io.github.arainko.ducktape

import munit.*

class NamedTupleSuite extends DucktapeSuite {
  test("named tuple to case class works") {
    case class Person(int: Int, str: String)

    assertTransforms((int = 1, str = "str"), Person(1, "str"))
  }

  test("case class to named tuple works") {
    case class Person(int: Int, str: String)
    assertTransforms(Person(1, "str"), (int = 1, str = "str"))
  }

  test("tuple to named tuple works") {
    assertTransforms((1, 2, 3), (field1 = 1, field2 = 2))
  }

  test("named tuple to function works") {
    def method(int: Int, str: String) = str + int
    val expected = "str1"
    val actual = (int = 1, str = "str").via(method)
    assertEquals(actual, expected)
  }

  test("named tuple to named tuple works") {
    assertTransforms((int = 1, str = "str"), (str = "str", int = 1))
  }

  test("nested named tuples work") {
    assertTransforms(
      (toplevel = (level1 = 1, level1Field = "level1Field")),
      (toplevel = (level1Field = "level1Field", level1 = 1))
    )
  }

  test("fallible (fail fast) named tuple to case class works") {
    case class Person(int: Int, str: String)

    val expected = Person(1, "str")

    Mode.FailFast.option.locally {
      assertTransformsFallible((str = Some("str"), int = Some(1)), Some(expected))
      assertTransformsFallible((str = Some("str"), int = None), Option.empty[Person])
    }
  }

  test("fallible (fail fast) case class to named tuple works") {
    case class Person(int: Option[Int], str: Option[String])

    Mode.FailFast.option.locally {
      assertTransformsFallible(Person(Some(1), Some("str")), Some((str = "str", int = 1)))
      assertTransformsFallible(Person(Some(1), None), Option.empty[(int: Int, str: String)])
    }
  }

  test("fallible (fail fast) tuple to named tuple works") {
    Mode.FailFast.option.locally {
      assertTransformsFallible((Some(1), Some(2), Some(3)), Some((field1 = 1, field2 = 2)))
      assertTransformsFallible((Some(1), Some(2), None), Some((field1 = 1, field2 = 2)))
      assertTransformsFallible((Some(1), None, None), Option.empty[(field1: Int, field2: Int)])
    }
  }

  test("fallible (fail fast) named tuple to function works") {
    def method(int: Int, str: String) = str + int
    val actual =
      Mode.FailFast.option.locally {
        (int = Some(1), str = Some("str")).fallibleVia(method)
      }
    assertEquals(actual, Some("str1"))
  }

  test("fallible (fail fast) named tuple to named tuple works") {
    Mode.FailFast.option.locally {
      assertTransformsFallible((int = Some(1), str = Some("str")), Some((str = "str", int = 1)))
      assertTransformsFallible((int = Some(1), str = None), Option.empty[(int: Int, str: String)])
    }
  }

  type Err[A] = Either[List[String], A]
  def success[A](value: A): Either[List[String], A] = Right(value)
  def fail[A](error: String): Either[List[String], A] = Left(List(error))

  test("fallible (accumulating) named tuple to case class works") {
    case class Person(int: Int, str: String)

    val expected = Person(1, "str")

    Mode.Accumulating.either[String, List].locally {
      assertTransformsFallible((str = success("str"), int = success(1)), success(expected))
      assertTransformsFallible((str = success("str"), int = fail("err")), fail[(int: Int, str: String)]("err"))
    }
  }

  test("fallible (accumulating) case class to named tuple works") {
    case class Person(int: Err[Int], str: Err[String])

    Mode.Accumulating.either[String, List].locally {
      assertTransformsFallible(Person(success(1), success("str")), success((str = "str", int = 1)))
      assertTransformsFallible(Person(success(1), fail("err")), fail[(int: Int, str: String)]("err"))
    }
  }

  test("fallible (accumulating) tuple to named tuple works") {
    Mode.Accumulating.either[String, List].locally {
      assertTransformsFallible((success(1), success(2), success(3)), success((field1 = 1, field2 = 2)))
      assertTransformsFallible((success(1), success(2), fail("err")), success((field1 = 1, field2 = 2)))
      assertTransformsFallible((success(1), fail("err"), fail("err")), fail[(field1: Int, field2: Int)]("err"))
    }
  }

  test("fallible (accumulating) named tuple to function works") {
    def method(int: Int, str: String) = str + int
    val actual =
      Mode.Accumulating.either[String, List].locally {
        (int = success(1), str = success("str")).fallibleVia(method)
      }
    assertEquals(actual, success("str1"))
  }

  test("fallible (accumulating) named tuple to named tuple works") {
    Mode.Accumulating.either[String, List].locally {
      assertTransformsFallible((int = success(1), str = success("str")), success((str = "str", int = 1)))
      assertTransformsFallible((int = success(1), str = fail("err")), fail[(int: Int, str: String)]("err"))
    }
  }

  test("source renames work") {
    assertTransformsConfigured(
      (INT = 1, STR = "str"),
      (int = 1, str = "str")
    )(Field.modifySourceNames(_.toLowerCase))
  }

  test("dest renames work") {
    assertTransformsConfigured(
      (int = 1, str = "str"),
      (INT = 1, STR = "str")
    )(Field.modifyDestNames(_.toLowerCase))
  }
}
