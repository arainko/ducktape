package io.github.arainko.ducktape

import io.github.arainko.TestRecord
// import io.github.arainko.TestEnum
// import io.github.arainko.ducktape.internal.Logger
import java.time.DayOfWeek
import io.github.arainko.TestEnum
import io.github.arainko.TestEnumReshuffled
import io.github.arainko.EnumParent
import io.github.arainko.StaticEnumParent

class JavaTransformationsSuite extends DucktapeSuite {
  test("transformation from Java records works") {
    val r = TestRecord(1, "asd")
    case class Bs(str: String, intField: Int)

    val bs = r.into[Bs].transform(Field.const(_.intField, 1))

    bs.into[TestRecord].transform(Field.const(_.intField(), 1))
  }

  test("transformation to Java records works") {
    val r = TestRecord(1, "asd")
    case class Bs(str: String, intField: Int)

    val bs = r.into[Bs].transform(Field.const(_.intField, 1))

    bs.into[TestRecord].transform(Field.const(_.intField(), 1))
  }

  test("transformations between Java records work") {
    import io.github.arainko.Records.*

    assertTransforms(
      SourceTopelevel(1, "str", SourceLevel1(SourceLevel2(3), "3")),
      DestTopelevel(1, "str", DestLevel1(DestLevel2(3), "3"))
    )
  }

  test("transformations between generic Java records work") {
    import io.github.arainko.Records.*

    assertTransforms(
      GenericToplevel(1, "str", SourceLevel1(SourceLevel2(3), "3")),
      DestTopelevel(1, "str", DestLevel1(DestLevel2(3), "3"))
    )

    assertTransforms(
      DestTopelevel(1, "str", DestLevel1(DestLevel2(3), "3")),
      GenericToplevel(1, "str", SourceLevel1(SourceLevel2(3), "3")),
    )
  }

  test("transformations between generic Java records behind a type alias work") {
    import io.github.arainko.Records.*

    type Source = GenericToplevel[SourceLevel1]

    type SourceGeneric[A] = GenericToplevel[A]

    assertTransforms[Source, DestTopelevel](
      GenericToplevel(1, "str", SourceLevel1(SourceLevel2(3), "3")),
      DestTopelevel(1, "str", DestLevel1(DestLevel2(3), "3"))
    )

     assertTransforms[SourceGeneric[SourceLevel1], DestTopelevel](
      GenericToplevel(1, "str", SourceLevel1(SourceLevel2(3), "3")),
      DestTopelevel(1, "str", DestLevel1(DestLevel2(3), "3"))
    )
  }

  test("tranformations for enums from Java stdlib work") {
    enum DOW {
      case Monday, Tuesday, Wednesday, Thursday, Friday, Saturday, Sunday
    }

    val mappings =
      Vector(
        DOW.Monday -> DayOfWeek.MONDAY,
        DOW.Tuesday -> DayOfWeek.TUESDAY,
        DOW.Wednesday -> DayOfWeek.WEDNESDAY,
        DOW.Thursday -> DayOfWeek.THURSDAY,
        DOW.Friday -> DayOfWeek.FRIDAY,
        DOW.Saturday -> DayOfWeek.SATURDAY,
        DOW.Sunday -> DayOfWeek.SUNDAY
      )

    mappings.foreach { (source, dest) =>
      assertTransformsConfigured(source, dest)(Case.modifySourceNames(_.toUpperCase))
      assertTransformsConfigured(dest, source)(Case.modifyDestNames(_.toUpperCase))
    }

    // symbolTermInfo[TestEnum]

    // symbolTermInfo[TestEnum.First.type]

    // impl sidenote
    // Java enums GET Mirrors but they can't be queried from macros unless a user triggers mirror resolution themselves, lol
    // for example: summon[Mirror.Of[TestEnum]] in user code would make the mirror appear in the macros as well, otherwise we get implicit resolution errors haha
    // enum ScalaEnum {
    //   case First, Second, Third
    // }

    // enum ScalaEnum2 {
    //   case First
    // }

    // sealed trait ScalaEnum3

    // object ScalaEnum3 {
    //   case object First extends ScalaEnum3
    // }

    // Logger.locally {
    //   ScalaEnum3.First.to[TestEnum.First.type]
    // }

    // val scalaToJava = ScalaEnum.Third.to[TestEnum]
    // val javaToScala = scalaToJava.to[ScalaEnum]
  }

  test("transformations between source-defined enums") {
    enum ScalaEnum {
      case First, Second, Third
    }

    val mappings =
      Vector(
        ScalaEnum.First  -> TestEnum.First,
        ScalaEnum.Second -> TestEnum.Second,
        ScalaEnum.Third  -> TestEnum.Third
      )

    mappings.foreach { (source, dest) =>
      assertTransforms(source, dest)
      assertTransforms(dest, source)
    }
  }

  test("transformations between static inner Java enums work") {
    enum ScalaEnum {
      case First, Second, Third
    }

    val mappings =
      Vector(
        ScalaEnum.First  -> StaticEnumParent.InnerEnum.First,
        ScalaEnum.Second -> StaticEnumParent.InnerEnum.Second,
        ScalaEnum.Third  -> StaticEnumParent.InnerEnum.Third
      )

    mappings.foreach { (source, dest) =>
      assertTransforms(source, dest)
      assertTransforms(dest, source)
    }
  }

  test("transformations between proper inner Java enums work") {
    enum ScalaEnum {
      case First, Second, Third
    }

    val mappings =
      Vector(
        ScalaEnum.First  -> EnumParent.InnerEnum.First,
        ScalaEnum.Second -> EnumParent.InnerEnum.Second,
        ScalaEnum.Third  -> EnumParent.InnerEnum.Third
      )

    mappings.foreach { (source, dest) =>
      assertTransforms(source, dest)
      assertTransforms(dest, source)
    }
  }

  test("transformations between Java-defined enums work") {
    enum ScalaEnum {
      case First, Second, Third
    }

    val mappings =
      Vector(
        ScalaEnum.First  -> TestEnum.First,
        ScalaEnum.Second -> TestEnum.Second,
        ScalaEnum.Third  -> TestEnum.Third
      )

    mappings.foreach { (source, dest) =>
      assertTransforms(source, dest)
      assertTransforms(dest, source)
    }

    // also verify round-trip between two Java enums with reshuffled ordinals
    val javaToJavaMappings =
      Vector(
        TestEnum.First  -> TestEnumReshuffled.First,
        TestEnum.Second -> TestEnumReshuffled.Second,
        TestEnum.Third  -> TestEnumReshuffled.Third
      )

    javaToJavaMappings.foreach { (source, dest) =>
      assertTransforms(source, dest)
      assertTransforms(dest, source)
    }
  }

  test("transformations between Java-defined singletons work") {
    sealed trait ScalaEnum

    object ScalaEnum {
      case object First extends ScalaEnum
    }

    val actual: TestEnum.First.type = ScalaEnum.First.to[TestEnum.First.type]

    assertEquals(actual, TestEnum.First)
  }

  test("transformations with configs for Java enums work") {
    enum ScalaEnum {
      case First, Second
    }

    assertTransformsConfigured(TestEnum.Third, ScalaEnum.Second)(
      Case.const(_.at[TestEnum.Third.type], ScalaEnum.Second)
    )
  }

  test("fallible transformations with Java enums work") {
    enum ScalaEnum {
      case First, Second
    }

    Mode.FailFast.option.locally {
      assertTransformsFallibleConfigured(TestEnum.Third, Option.empty[ScalaEnum])(
        Case.fallibleConst(_.at[TestEnum.Third.type], None)
      )
    }
  }
}
