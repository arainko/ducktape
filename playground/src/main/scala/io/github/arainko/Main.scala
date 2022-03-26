package io.github.arainko

import scala.collection.mutable.ArrayBuilder
import scala.deriving.Mirror
import scala.runtime.Scala3RunTime

final case class Person(name: String, age: Int)
final case class SecondPerson(age: Int, name: String, costam: String)

enum Color {
  case Red, Blue, Green
  case Custom(value: Int)
}

sealed trait TraitColor

object TraitColor {
  case object Red extends TraitColor
  case object Blue extends TraitColor
  case object Green extends TraitColor
  case class Custom(value: Int) extends TraitColor
}

@main def run = {

  val m = summon[Mirror.SumOf[Color]]
  val m2 = summon[Mirror.SumOf[TraitColor]]
  m.ordinal(Color.Red)
  Color.Custom(1).ordinal

  Macros.transformCoproduct[Color, TraitColor](Color.Blue)

  Macros.structure {
    Color.Blue match {
      case Color.Red => 1
      case Color.Blue => 2
      case Color.Green => 3
      case Color.Custom(value) => value
    }
  }

  // val initialPerson = Person("init", 2)

  // val initialSecondPerson = SecondPerson(2, "initSec", "costam")

  // val transformed = Macros.transform[SecondPerson, Person](initialSecondPerson)

  // val builder = Macros.structure {
  //   Builder[Person, SecondPerson, EmptyTuple](Map.empty, Map.empty, Map.empty)
  //     .withFieldConstant(_.costam, "costam123")
  //     .withFieldComputed(_.age, _.age + 1)
  //     .withFieldRenamed(_.costam, _.name)
  //     .run(initialPerson)
  // }


  // Macros.resolveTrans[Int]
  // println(builder)
  // Macros.structure(initialPerson.age)

  // val companion = Macros.companion[Person]
  // println(Macros.structure(Person(name = "name", age = 1)))
  // println(Macros.structure(Person("name", 1)))

}
