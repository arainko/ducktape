package io.github.arainko.ducktapetest.builder

import io.github.arainko.ducktape.*
import io.github.arainko.ducktapetest.model.*
import munit.FunSuite
import scala.compiletime.testing.*

case class Inner(field1: Int, field2: String, field3: Double)

case class InnerTransformed(field3: Double, field2: String, field1: Int)

case class LessFields(field1: Int, field2: String, field3: Inner, extraField: List[Int], renamedFromField: Int)

case class MoreFields(
  field3: InnerTransformed,
  field1: Int,
  field2: String,
  computedField: Map[String, String],
  constantField: String,
  renamedToField: Int
)

class BuilderConfigurationSuite extends FunSuite {
  test("a case class with less fields should be transformable to a case class with more fields if configured properly") {
    val lessFields = LessFields(field1 = 1, field2 = "2", field3 = Inner(3, "3", 3d), extraField = List(4), renamedFromField = 5)

    val actual = lessFields
      .into[MoreFields]
      .withFieldRenamed(_.renamedToField, _.renamedFromField)
      .withFieldConst(_.constantField, "CONSTANT")
      .withFieldComputed(_.computedField, from => Map(from.field2 -> from.field2))
      .transform

    val expected = MoreFields(
      field3 = InnerTransformed(3d, "3", 3),
      field1 = 1,
      field2 = "2",
      computedField = Map("2" -> "2"),
      constantField = "CONSTANT",
      renamedToField = 5
    )

    assertEquals(actual, expected)
  }

  test(
    "transforming into a case class with less fields into a case classwith more fields should fail to compile if not configured properly"
  ) {

    val errors = typeCheckErrors {
      """
      val lessFields = LessFields(field1 = 1, field2 = "2", field3 = Inner(3, "3", 3d), extraField = List(4), renamedFromField = 5)

      val actual = lessFields
        .into[MoreFields]
        .withFieldRenamed(_.renamedToField, _.renamedFromField)
        .withFieldComputed(_.computedField, from => Map(from.field2 -> from.field2))
        .transform
      """
    }.map(_.message).mkString

    assertEquals(errors, "No field named 'constantField' found in LessFields")
  }
}
