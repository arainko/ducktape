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
  case INT(FIELD1: Int)
  case STR, DOUBLE, BIG_ASS_NAME
}


sealed trait Dupal 

object Dupal {
  sealed trait Level1Dupal extends Dupal
  sealed trait Level2Dupal extends Dupal

  case class Dupal1Impl(int: Int, str: String) extends Level1Dupal
  case class Dupal2Impl(int: Int, str: String) extends Level2Dupal
}

sealed trait DupalDest

object DupalDest {
  sealed trait Level1Dupal extends DupalDest
  sealed trait Level2Dupal extends DupalDest

  case class Dupal1Impl(int: Int, str: String) extends Level1Dupal
  case class Dupal2Impl(int: Int, str: String) extends Level2Dupal
}


object a extends App {

  val src1: Dupal = Dupal.Dupal1Impl(3, "asd")

  println {
  Transformer.Debug.showCode {
  src1.into[DupalDest].transform(
    Field.const(_.at[DupalDest.Level1Dupal].at[DupalDest.Dupal1Impl].int, 1)
  )
  }
}

  // val src: SourceEnum = ???

  // TestSnakeCase.into[TEST_SNAKE_CASE.type].transform(
  //   Case.modifySourceNames(_.toLowerCase),
  //   Case.modifyDestNames(dupal)
  // )

  // private inline def dupal(renamer: Renamer): Renamer = renamer.toLowerCase.replace("_", "")

  // src.into[DestEnum].transform(
  //   Field.modifySourceNames(_.toLowerCase).local(a => a),
  //   Case.modifyDestNames(_.toLowerCase).local(a => a),
  //   Case.modifySourceNames(_.toLowerCase.replace("_", "")).local(a => a)
  // )
  
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
