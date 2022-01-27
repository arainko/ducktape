package io.github.arainko.ducktape.matchtypes

import munit.FunSuite
import io.github.arainko.ducktape.*
import scala.deriving.Mirror

object FieldSuite:
  final case class Product(field1: Int, field2: String, field3: Double)

end FieldSuite

class FieldSuite extends FunSuite {

  private val mirror = summon[Mirror.Of[FieldSuite.Product]]

  test("Field.FromLabelsAndTypes has proper type") {
    type Expected =
      Field["field1", Int] *:
        Field["field2", String] *:
        Field["field3", Double] *:
        EmptyTuple

    type Actual = Field.FromLabelsAndTypes[mirror.MirroredElemLabels, mirror.MirroredElemTypes]
    
    summon[Actual =:= Expected]
  }

  test("Field.DropByLabel drops an element from a tuple based on its label") {
    type Expected =
      Field["field1", Int] *:
        Field["field3", Double] *:
        EmptyTuple

    type Fields = Field.FromLabelsAndTypes[mirror.MirroredElemLabels, mirror.MirroredElemTypes]
    type Actual = Field.DropByLabel["field2", Fields]

    summon[Actual =:= Expected]
  }

  test("Field.TypeForLabel fetches a type based on its label") {
    type Expected = Double

    type Fields = Field.FromLabelsAndTypes[mirror.MirroredElemLabels, mirror.MirroredElemTypes]
    type Actual = Field.TypeForLabel["field3", Fields]

    summon[Actual =:= Expected]
  }

  test("Field.TypeForLabel returns Nothing if a type with a given label is not found") {
    type Expected = Nothing

    type Fields = Field.FromLabelsAndTypes[mirror.MirroredElemLabels, mirror.MirroredElemTypes]
    type Actual = Field.TypeForLabel["not existing field", Fields]

    summon[Actual =:= Expected]
  }
}
