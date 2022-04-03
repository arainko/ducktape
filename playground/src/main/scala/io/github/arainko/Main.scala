package io.github.arainko

import scala.deriving.*
// import io.github.arainko.internal.*

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

final case class Generic[A](costam: Int, gen: A)

@main def run = {
  val secondPerson = SecondPerson(1, "", "")
  // Mirror

  println {
    Macros.code {
      Transformer[Generic[Int], Generic[Option[Int]]]
    }
  }

  // println {
  //   Macros.structure {
  //     new Person("name", 1)
  //   }
  // }

  // println(res)

}
