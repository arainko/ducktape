package io.github.arainko

import scala.collection.mutable.ArrayBuilder
import scala.deriving.Mirror
import scala.runtime.Scala3RunTime
import io.github.arainko.internal.*

final case class Person(name: String, age: Int)
final case class SecondPerson(age: Int, name: String, costam: String)

enum Color {
  case Red, Blue, Green
}

sealed trait TraitColor

object TraitColor {
  case object Red extends TraitColor
  case object Blue extends TraitColor
  case object Green extends TraitColor
}

@main def run = {
  val secondPerson = SecondPerson(1, "", "")

  println {
    Macros.code {
      CoproductTransformerMacros.transform[Color, TraitColor](Color.Blue)
    }
  }

  // println(res)

}
