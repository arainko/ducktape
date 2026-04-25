package io.github.arainko.ducktape

import java.time.DayOfWeek
import io.github.arainko.TestRecord
import io.github.arainko.TestEnum
import io.github.arainko.TestEnumReshuffled
import io.github.arainko.EnumParent
import io.github.arainko.StaticEnumParent

class JavaTransformationsSuite extends DucktapeSuite {
  test("transformation from Java records to Scala case classes works") {
    import io.github.arainko.Records.*

    final case class ScalaToplevel(intField: Int, str: String, level1: ScalaLevel1)
    final case class ScalaLevel1(level2: ScalaLevel2, str: String)
    final case class ScalaLevel2(intField: Int)

    assertTransforms(
      SourceTopelevel(1, "str", SourceLevel1(SourceLevel2(3), "nested")),
      ScalaToplevel(1, "str", ScalaLevel1(ScalaLevel2(3), "nested"))
    )
  }

  test("transformation from Scala case classes to Java records works") {
    import io.github.arainko.Records.*

    final case class ScalaToplevel(intField: Int, str: String, level1: ScalaLevel1)
    final case class ScalaLevel1(level2: ScalaLevel2, str: String)
    final case class ScalaLevel2(intField: Int)

    assertTransforms(
      ScalaToplevel(1, "str", ScalaLevel1(ScalaLevel2(3), "nested")),
      DestTopelevel(1, "str", DestLevel1(DestLevel2(3), "nested"))
    )
  }

  test("configs work when transforming from Java records to Scala case classes") {
    import io.github.arainko.Records.*

    final case class ScalaToplevel(intField: Int, str: String, level1: ScalaLevel1)
    final case class ScalaLevel1(level2: ScalaLevel2, str: String)
    final case class ScalaLevel2(intField: Int, extra: String)

    val source = SourceTopelevel(1, "str", SourceLevel1(SourceLevel2(3), "nested"))
    val expected = ScalaToplevel(1, "str", ScalaLevel1(ScalaLevel2(3, "configured"), "nested"))

    assertTransformsConfigured(source, expected)(
      Field.const(_.level1.level2.extra, "configured")
    )
  }

  test("configs work when transforming from Scala case classes to Java records") {
    import io.github.arainko.Records.*

    final case class ScalaToplevel(intField: Int, str: String, level1: ScalaLevel1)
    final case class ScalaLevel1(level2: ScalaLevel2, str: String)
    final case class ScalaLevel2(intField: Int)

    val source = ScalaToplevel(1, "str", ScalaLevel1(ScalaLevel2(3), "nested"))
    val expected = DestTopelevel(1, "str", DestLevel1(DestLevel2(30), "nested"))

    assertTransformsConfigured(source, expected)(
      Field.const(_.level1().level2().intField(), 30)
    )
  }

  test("Field.allMatching works when the field source is a Java record") {
    final case class Source(intField: Int)
    final case class Dest(intField: Int, str: String)

    val source = Source(1)
    val fieldSource = TestRecord(2, "configured")
    val expected = Dest(2, "configured")

    assertEachEquals(
      source.into[Dest].transform(Field.allMatching(fieldSource)),
      source.intoVia(Dest.apply).transform(Field.allMatching(fieldSource)),
      Transformer.define[Source, Dest].build(Field.allMatching(fieldSource)).transform(source),
      Transformer.defineVia[Source](Dest.apply).build(Field.allMatching(fieldSource)).transform(source)
    )(expected)
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

  test("configs work when transforming from generic Java records to Scala case classes") {
    import io.github.arainko.Records.*

    final case class ScalaToplevel(intField: Int, str: String, level1: ScalaLevel1)
    final case class ScalaLevel1(level2: ScalaLevel2, str: String)
    final case class ScalaLevel2(intField: Int, extra: String)

    val source = GenericToplevel(1, "str", SourceLevel1(SourceLevel2(3), "nested"))
    val expected = ScalaToplevel(1, "str", ScalaLevel1(ScalaLevel2(3, "configured"), "nested"))

    assertTransformsConfigured(source, expected)(
      Field.const(_.level1.level2.extra, "configured")
    )
  }

  test("configs work when transforming from generic Java records to Scala case classes behind an alias") {
    import io.github.arainko.Records.*

    final case class ScalaToplevel(intField: Int, str: String, level1: ScalaLevel1)
    final case class ScalaLevel1(level2: ScalaLevel2, str: String)
    final case class ScalaLevel2(intField: Int, extra: String)

    type Alias = ScalaToplevel

    val source = GenericToplevel(1, "str", SourceLevel1(SourceLevel2(3), "nested"))
    val expected = ScalaToplevel(1, "str", ScalaLevel1(ScalaLevel2(3, "configured"), "nested"))

    assertTransformsConfigured[GenericToplevel[SourceLevel1], Alias](source, expected)(
      Field.const(_.level1.level2.extra, "configured")
    )
  }

  test("configs work when transforming from Scala case classes to generic Java records") {
    import io.github.arainko.Records.*

    final case class ScalaToplevel(intField: Int, str: String, level1: ScalaLevel1)
    final case class ScalaLevel1(level2: ScalaLevel2, str: String)
    final case class ScalaLevel2(intField: Int)

    val source = ScalaToplevel(1, "str", ScalaLevel1(ScalaLevel2(3), "nested"))
    val expected = GenericToplevel(1, "str", DestLevel1(DestLevel2(30), "nested"))

    assertTransformsConfigured(source, expected)(
      Field.const(_.level1().level2().intField(), 30)
    )
  }

  test("configs work when transforming from Scala case classes to generic Java records behind a type alias") {
    import io.github.arainko.Records.*

    final case class ScalaToplevel(intField: Int, str: String, level1: ScalaLevel1)
    final case class ScalaLevel1(level2: ScalaLevel2, str: String)
    final case class ScalaLevel2(intField: Int)

    type GenericDest = GenericToplevel[DestLevel1]
    // TODO: using the above messes things up during Strcuture resolution, it resolves to 'Ordinary' instead of 'Product'

    val source = ScalaToplevel(1, "str", ScalaLevel1(ScalaLevel2(3), "nested"))
    val expected = GenericToplevel(1, "str", DestLevel1(DestLevel2(30), "nested"))

    assertTransformsConfigured[ScalaToplevel, GenericDest](source, expected)(
      Field.const(_.level1().level2().intField(), 30)
    )
  }

  test("transformations between generic Java records behind a type alias work (from)") {
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

  test("transformations between generic Java records behind a type alias work (to)") {
    import io.github.arainko.Records.*

    type Dest = GenericToplevel[SourceLevel1]

    type DestGeneric[A] = GenericToplevel[A]

    assertTransforms[DestTopelevel, Dest](
      DestTopelevel(1, "str", DestLevel1(DestLevel2(3), "3")),
      GenericToplevel(1, "str", SourceLevel1(SourceLevel2(3), "3")),
    )

     assertTransforms[DestTopelevel, DestGeneric[SourceLevel1]](
      DestTopelevel(1, "str", DestLevel1(DestLevel2(3), "3")),
      GenericToplevel(1, "str", SourceLevel1(SourceLevel2(3), "3")),
    )
  }

  test("fallible transformations from Java records to Scala case classes work") {
    import io.github.arainko.Records.*

    final case class ScalaToplevel(intField: Int, str: Int, level1: ScalaLevel1)
    final case class ScalaLevel1(level2: ScalaLevel2, str: Int)
    final case class ScalaLevel2(intField: Int)
    type Source = SourceTopelevel
    type Dest = ScalaToplevel

    given Transformer.Fallible[Option, String, Int] = _.toIntOption

    Mode.FailFast.option.locally {
      val source = SourceTopelevel(1, "2", SourceLevel1(SourceLevel2(3), "4"))
      val expected = Some(ScalaToplevel(1, 2, ScalaLevel1(ScalaLevel2(3), 4)))

      assertTransformsFallible(source, expected)
      assertTransformsFallible[Option, Mode.FailFast[Option], Source, Dest](source, expected)
    }
  }

  test("fallible transformations from Scala case classes to Java records work") {
    import io.github.arainko.Records.*

    final case class ScalaToplevel(intField: String, str: String, level1: ScalaLevel1)
    final case class ScalaLevel1(level2: ScalaLevel2, str: String)
    final case class ScalaLevel2(intField: String)
    type Source = ScalaToplevel
    type Dest = DestTopelevel

    given Transformer.Fallible[Option, String, Int] = _.toIntOption

    Mode.FailFast.option.locally {
      val source = ScalaToplevel("1", "str", ScalaLevel1(ScalaLevel2("3"), "nested"))
      val expected = Some(DestTopelevel(1, "str", DestLevel1(DestLevel2(3), "nested")))

      assertTransformsFallible(source, expected)
      assertTransformsFallible[Option, Mode.FailFast[Option], Source, Dest](source, expected)
    }
  }

  test("fallible transformations between Java records work") {
    import io.github.arainko.Records.*

    final case class ScalaLevel1(level2: ScalaLevel2, str: String)
    final case class ScalaLevel2(intField: String)
    type Source = GenericToplevel[ScalaLevel1]
    type Dest = DestTopelevel

    given Transformer.Fallible[Option, String, Int] = _.toIntOption

    Mode.FailFast.option.locally {
      val source = GenericToplevel(1, "str", ScalaLevel1(ScalaLevel2("3"), "nested"))
      val expected = Some(DestTopelevel(1, "str", DestLevel1(DestLevel2(3), "nested")))

      assertTransformsFallible(source, expected)
      assertTransformsFallible[Option, Mode.FailFast[Option], Source, Dest](source, expected)
    }
  }

  test("fallible transformations with configs from Java records to Scala case classes work") {
    import io.github.arainko.Records.*

    final case class ScalaToplevel(intField: Int, str: Int, level1: ScalaLevel1)
    final case class ScalaLevel1(level2: ScalaLevel2, str: Int)
    final case class ScalaLevel2(intField: Int, extra: Int)
    type Source = SourceTopelevel
    type Dest = ScalaToplevel

    given Transformer.Fallible[Option, String, Int] = _.toIntOption

    Mode.FailFast.option.locally {
      val source = SourceTopelevel(1, "2", SourceLevel1(SourceLevel2(3), "4"))
      val expected = Some(ScalaToplevel(1, 2, ScalaLevel1(ScalaLevel2(3, 4), 4)))

      assertTransformsFallibleConfigured(source, expected)(
        Field.fallibleComputed(_.level1.level2.extra, src => src.level1.str.toIntOption)
      )
      assertTransformsFallibleConfigured[Option, Mode.FailFast[Option], Source, Dest](source, expected)(
        Field.fallibleComputed(_.level1.level2.extra, src => src.level1.str.toIntOption)
      )
    }
  }

  test("fallible transformations with configs from Scala case classes to Java records work") {
    import io.github.arainko.Records.*

    final case class ScalaToplevel(intField: String, str: String, level1: ScalaLevel1)
    final case class ScalaLevel1(level2: ScalaLevel2, str: String)
    final case class ScalaLevel2(intField: String)
    type Source = ScalaToplevel
    type Dest = DestTopelevel

    given Transformer.Fallible[Option, String, Int] = _.toIntOption

    Mode.FailFast.option.locally {
      val source = ScalaToplevel("1", "2", ScalaLevel1(ScalaLevel2("3"), "4"))
      val expected = Some(DestTopelevel(1, "2", DestLevel1(DestLevel2(4), "4")))

      assertTransformsFallibleConfigured(source, expected)(
        Field.fallibleComputed(_.level1().level2().intField(), src => src.level1.str.toIntOption)
      )
      assertTransformsFallibleConfigured[Option, Mode.FailFast[Option], Source, Dest](source, expected)(
        Field.fallibleComputed(_.level1().level2().intField(), src => src.level1.str.toIntOption)
      )
    }
  }

  test("fallible transformations with configs between Java records work") {
    import io.github.arainko.Records.*

    final case class ScalaLevel1(level2: ScalaLevel2, str: String)
    final case class ScalaLevel2(intField: String)
    type Source = GenericToplevel[ScalaLevel1]
    type Dest = DestTopelevel

    given Transformer.Fallible[Option, String, Int] = _.toIntOption

    Mode.FailFast.option.locally {
      val source = GenericToplevel(1, "2", ScalaLevel1(ScalaLevel2("3"), "4"))
      val expected = Some(DestTopelevel(1, "2", DestLevel1(DestLevel2(4), "4")))

      assertTransformsFallibleConfigured(source, expected)(
        Field.fallibleComputed(_.level1().level2().intField(), src => src.level1().str.toIntOption)
      )
      assertTransformsFallibleConfigured[Option, Mode.FailFast[Option], Source, Dest](source, expected)(
        Field.fallibleComputed(_.level1().level2().intField(), src => src.level1().str.toIntOption)
      )
    }
  }

  test("tranformations for enums from Java stdlib work") {
    enum DOW {
      case Monday, Tuesday, Wednesday, Thursday, Friday, Saturday, Sunday
    }
    type Source = DOW
    type Dest = DayOfWeek

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
      assertTransformsConfigured[Source, Dest](source, dest)(Case.modifySourceNames(_.toUpperCase))
      assertTransformsConfigured[Dest, Source](dest, source)(Case.modifyDestNames(_.toUpperCase))
    }
  }

  test("transformations between source-defined enums") {
    enum ScalaEnum {
      case First, Second, Third
    }
    type Source = ScalaEnum
    type Dest = TestEnum

    val mappings =
      Vector(
        ScalaEnum.First  -> TestEnum.First,
        ScalaEnum.Second -> TestEnum.Second,
        ScalaEnum.Third  -> TestEnum.Third
      )

    mappings.foreach { (source, dest) =>
      assertTransforms(source, dest)
      assertTransforms(dest, source)
      assertTransforms[Source, Dest](source, dest)
      assertTransforms[Dest, Source](dest, source)
    }
  }

  test("transformations between static inner Java enums work") {
    enum ScalaEnum {
      case First, Second, Third
    }
    type Source = ScalaEnum
    type Dest = StaticEnumParent.InnerEnum

    val mappings =
      Vector(
        ScalaEnum.First  -> StaticEnumParent.InnerEnum.First,
        ScalaEnum.Second -> StaticEnumParent.InnerEnum.Second,
        ScalaEnum.Third  -> StaticEnumParent.InnerEnum.Third
      )

    mappings.foreach { (source, dest) =>
      assertTransforms(source, dest)
      assertTransforms(dest, source)
      assertTransforms[Source, Dest](source, dest)
      assertTransforms[Dest, Source](dest, source)
    }
  }

  test("transformations between proper inner Java enums work") {
    enum ScalaEnum {
      case First, Second, Third
    }
    type Source = ScalaEnum
    type Dest = EnumParent.InnerEnum

    val mappings =
      Vector(
        ScalaEnum.First  -> EnumParent.InnerEnum.First,
        ScalaEnum.Second -> EnumParent.InnerEnum.Second,
        ScalaEnum.Third  -> EnumParent.InnerEnum.Third
      )

    mappings.foreach { (source, dest) =>
      assertTransforms(source, dest)
      assertTransforms(dest, source)
      assertTransforms[Source, Dest](source, dest)
      assertTransforms[Dest, Source](dest, source)
    }
  }

  test("transformations between Java-defined enums work") {
    enum ScalaEnum {
      case First, Second, Third
    }
    type ScalaSource = ScalaEnum
    type JavaDest = TestEnum
    type JavaSource = TestEnum
    type JavaReshuffledDest = TestEnumReshuffled

    val mappings =
      Vector(
        ScalaEnum.First  -> TestEnum.First,
        ScalaEnum.Second -> TestEnum.Second,
        ScalaEnum.Third  -> TestEnum.Third
      )

    mappings.foreach { (source, dest) =>
      assertTransforms(source, dest)
      assertTransforms(dest, source)
      assertTransforms[ScalaSource, JavaDest](source, dest)
      assertTransforms[JavaDest, ScalaSource](dest, source)
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
      assertTransforms[JavaSource, JavaReshuffledDest](source, dest)
      assertTransforms[JavaReshuffledDest, JavaSource](dest, source)
    }
  }

  test("transformations between Java-defined singletons work") {
    sealed trait ScalaEnum

    object ScalaEnum {
      case object First extends ScalaEnum
    }
    type Source = ScalaEnum.First.type
    type Dest = TestEnum.First.type

    val actual: TestEnum.First.type = ScalaEnum.First.to[TestEnum.First.type]
    val aliasedActual: Dest = ScalaEnum.First.to[Dest]

    assertEquals(actual, TestEnum.First)
    assertEquals(aliasedActual, TestEnum.First)
  }

  test("transformations with configs for Java enums work") {
    enum ScalaEnum {
      case First, Second
    }
    type Source = TestEnum
    type Dest = ScalaEnum

    assertTransformsConfigured(TestEnum.Third, ScalaEnum.Second)(
      Case.const(_.at[TestEnum.Third.type], ScalaEnum.Second)
    )
    assertTransformsConfigured[Source, Dest](TestEnum.Third, ScalaEnum.Second)(
      Case.const(_.at[TestEnum.Third.type], ScalaEnum.Second)
    )
  }

  test("fallible transformations with Java enums work") {
    enum ScalaEnum {
      case First, Second
    }
    type Source = TestEnum
    type Dest = ScalaEnum

    Mode.FailFast.option.locally {
      assertTransformsFallibleConfigured(TestEnum.Third, Option.empty[ScalaEnum])(
        Case.fallibleConst(_.at[TestEnum.Third.type], None)
      )
      assertTransformsFallibleConfigured[Option, Mode.FailFast[Option], Source, Dest](TestEnum.Third, Option.empty[ScalaEnum])(
        Case.fallibleConst(_.at[TestEnum.Third.type], None)
      )
    }
  }
}
