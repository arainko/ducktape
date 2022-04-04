package io.github.arainko

import scala.deriving.*
// import io.github.arainko.internal.*

final case class Person(name: String, age: Int)
final case class SecondPerson(age: Int, name: String, costam: String)

enum Color {
  case Blue, Red, Green
}

sealed trait TraitColor

object TraitColor {
  case object Green extends TraitColor
  case object Red extends TraitColor
  case object Blue extends TraitColor
  case object Costam extends TraitColor
}

final case class Generic[A](costam: Int, gen: Option[Generic[A]])

@main def run = {
  val secondPerson = SecondPerson(1, "", "")
  // Mirror

  println {
    // Macros.symbols[Color]
    Macros.code {
      Transformer[Color, TraitColor].transform(Color.Green)
    }
  }


  val cos: Color.Red.type = Color.Red

  // println {
  //   Macros.structure {
  //     new Person("name", 1)
  //   }
  // }

  // println(res)

}
