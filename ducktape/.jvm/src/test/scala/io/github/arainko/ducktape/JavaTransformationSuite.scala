package io.github.arainko.ducktape

import io.github.arainko.TestRecord
import io.github.arainko.TestEnum

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

  test("") {
    //impl sidenote
    // Java enums GET Mirrors but they can't be queried from macros unless a user triggers mirror resolution themselves, lol
    // for example: summon[Mirror.Of[TestEnum]] in user code would make the mirror appear in the macros as well, otherwise we get implicit resolution errors haha
    enum ScalaEnum {
      case First, Second, Third
    }
    
    val scalaToJava = ScalaEnum.Third.to[TestEnum]
    val javaToScala = scalaToJava.to[ScalaEnum]
  }
}
