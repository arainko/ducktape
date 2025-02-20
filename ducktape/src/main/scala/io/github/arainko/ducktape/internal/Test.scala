package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.*

final case class Test(int: Int, level1: LevelSource1)
final case class TestDest(int: Option[Int], level1: LevelDest1)

case class LevelSource1(INT: Int, int: Int)
case class LevelDest1(int: Int, INT: Int)


enum SourceEnum {
  case int(FIELD1: Int)
  case str, double
}

enum DestEnum {
  case INT(FIELD1: Int)
  case STR, DOUBLE
}


object a {
  val src: SourceEnum = ???

  Transformer.Debug.showCode {

    src.into[DestEnum].transform(Case.modifyNames)
  }

  // Transformer.Debug.showCode {
    // src.into[TestDest].transform(Field.modifyName.regional(_.level1))


  // }


}
