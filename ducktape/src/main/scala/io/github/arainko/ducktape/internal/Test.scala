package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.*

final case class Test(int: Int, level1: LevelSource1)
final case class TestDest(int: Option[Int], level1: LevelDest1, extra: Int = 2137)

case class LevelSource1(int: Int)
case class LevelDest1(int: Int, int2: Int = 2137)


object a {
  val src: Test = ???

  // Transformer.Debug.showCode {
    src.to[TestDest]


  // }


}
