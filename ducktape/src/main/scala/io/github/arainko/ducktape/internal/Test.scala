package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.*

final case class Test(int: Int, level1: LevelSource1)

final case class TestDest(int: Option[Int], level1: LevelDest1)

case class LevelSource1(INT: Int)

case class LevelDest1(int: Int)


enum DestEnum {
  case int(field1: Int)
  case str, double
}

enum SourceEnum {
  case INT(field1: Int)
  case STR, DOUBLE
}


object a {
  val src: Test = ???

  
  Transformer.Debug.showCode {
    src.into[TestDest].transform(
      Field.modifySourceNames(_.rename("INT", "int")),
      // Field.modifyDestNames(_.toUpperCase),
    )

    // src.into[]
  }

  // Transformer.Debug.showCode {
    // src.into[TestDest].transform(Field.modifyName.regional(_.level1))


  // }


}
