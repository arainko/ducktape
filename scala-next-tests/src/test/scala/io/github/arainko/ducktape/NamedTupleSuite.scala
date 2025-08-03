package io.github.arainko.ducktape

import munit.*
import io.github.arainko.ducktape.internal.CodePrinter

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

  test("big named tuple to big case class works") {
    type BigNamedTuple = (
      field1: Int,
      field2: Int,
      field3: Int,
      field4: Int,
      field5: Int,
      field6: Int,
      field7: Int,
      field8: Int,
      field9: Int,
      field10: Int,
      field11: Int,
      field12: Int,
      field13: Int,
      field14: Int,
      field15: Int,
      field16: Int,
      field17: Int,
      field18: Int,
      field19: Int,
      field20: Int,
      field21: Int,
      field22: Int,
      field23: Int
    )
    case class Big(
      field1: Int,
      field2: Int,
      field3: Int,
      field4: Int,
      field5: Int,
      field6: Int,
      field7: Int,
      field8: Int,
      field9: Int,
      field10: Int,
      field11: Int,
      field12: Int,
      field13: Int,
      field14: Int,
      field15: Int,
      field16: Int,
      field17: Int,
      field18: Int,
      field19: Int,
      field20: Int,
      field21: Int,
      field22: Int,
      field23: Int
    )
    val input: BigNamedTuple = (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23)
    val expected = Big(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23)

    assertTransforms(
      input,
      expected
    )
  }

  test("big case class to big named tuple works") {
    type BigNamedTuple = (
      field1: Int,
      field2: Int,
      field3: Int,
      field4: Int,
      field5: Int,
      field6: Int,
      field7: Int,
      field8: Int,
      field9: Int,
      field10: Int,
      field11: Int,
      field12: Int,
      field13: Int,
      field14: Int,
      field15: Int,
      field16: Int,
      field17: Int,
      field18: Int,
      field19: Int,
      field20: Int,
      field21: Int,
      field22: Int,
      field23: Int
    )
    case class Big(
      field1: Int,
      field2: Int,
      field3: Int,
      field4: Int,
      field5: Int,
      field6: Int,
      field7: Int,
      field8: Int,
      field9: Int,
      field10: Int,
      field11: Int,
      field12: Int,
      field13: Int,
      field14: Int,
      field15: Int,
      field16: Int,
      field17: Int,
      field18: Int,
      field19: Int,
      field20: Int,
      field21: Int,
      field22: Int,
      field23: Int
    )
    val expected: BigNamedTuple = (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23)
    val input = Big(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23)

    assertTransforms(
      input,
      expected
    )
  }

  test("big named tuple to big named tuple works") {
    type BigNamedTuple = (
      field1: Int,
      field2: Int,
      field3: Int,
      field4: Int,
      field5: Int,
      field6: Int,
      field7: Int,
      field8: Int,
      field9: Int,
      field10: Int,
      field11: Int,
      field12: Int,
      field13: Int,
      field14: Int,
      field15: Int,
      field16: Int,
      field17: Int,
      field18: Int,
      field19: Int,
      field20: Int,
      field21: Int,
      field22: Int,
      field23: Int
    )
    type BigNamedTuple2 = (
      field23: Int,
      field2: Int,
      field3: Int,
      field4: Int,
      field5: Int,
      field6: Int,
      field7: Int,
      field8: Int,
      field9: Int,
      field10: Int,
      field11: Int,
      field12: Int,
      field13: Int,
      field14: Int,
      field15: Int,
      field16: Int,
      field17: Int,
      field18: Int,
      field19: Int,
      field20: Int,
      field21: Int,
      field22: Int,
      field1: Int
    )
    val input: BigNamedTuple = (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23)
    val expected: BigNamedTuple2 = (23, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 1)

    assertTransforms(
      input,
      expected
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

  test("path selectors on named tuples work") {
    import io.github.arainko.ducktape.internal.*
    val input = (toplevel = (level1 = (level2 = 1, field = 2)))

    Logger.locally {
      PathSelector.invoke(((i: input.type) => i.toplevel.level1.level2))
    }

    CodePrinter.structure:
      ((i: input.type) => i.toplevel)

    // input
    //   .into[(toplevel: (level1: (level2: Int, field: Int)))]
    //   .transform(
    //     Field.const(_.toplevel.level1.field, 1)
    //   )
  }
}
