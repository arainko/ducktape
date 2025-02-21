package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.*

final case class Test(int: Int, level1: LevelSource1)

final case class TestDest(int: Option[Int], level1: LevelDest1)

case class LevelSource1(INT: Int)

case class LevelDest1(int: Int)


case object TEST_SNAKE_CASE

case object TestSnakeCase


enum DestEnum {
  case int(field1: Int)
  case str, double, bigAssName
}

enum SourceEnum {
  case INT(field1: Int)
  case STR, DOUBLE, BIG_ASS_NAME
}


object a {
  val src: SourceEnum = ???

  TestSnakeCase.into[TEST_SNAKE_CASE.type].transform(
    Case.modifySourceNames(_.toLowerCase),
    Case.modifyDestNames(dupal)
  )

  private inline def dupal(renamer: Renamer): Renamer = renamer.toLowerCase.replace("_", "")

  src.into[DestEnum].transform(
    Case.modifyDestNames(_.toLowerCase).local(_.at[DestEnum.bigAssName.type]),
    Case.modifySourceNames(_.toLowerCase.replace("_", ""))
  )
  
  // Transformer.Debug.showCode {
  //   src.into[TestDest].transform(
  //     Field.modifySourceNames(_.rename("INT", "int")),
  //     // Field.modifyDestNames(_.toUpperCase),
  //   )

  //   // src.into[]
  // }

  // Transformer.Debug.showCode {
    // src.into[TestDest].transform(Field.modifyName.regional(_.level1))


  // }


}
