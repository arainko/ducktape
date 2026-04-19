package io.github.arainko.ducktape

import io.github.arainko.TestRecord

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
}
