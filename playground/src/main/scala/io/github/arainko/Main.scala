package io.github.arainko

import scala.collection.mutable.ArrayBuilder

final case class Person(name: String, age: Int)
final case class SecondPerson(age: Int, name: String, costam: String)

@main def run = {

  val initialPerson = Person("init", 2)

  val initialSecondPerson = SecondPerson(2, "initSec", "costam")

  val transformed = Macros.transform[SecondPerson, Person](initialSecondPerson)

  // val builder = Macros.structure {
  //   Builder[Person, SecondPerson, EmptyTuple](Map.empty)
  //     .withFieldConstant(_.costam, "costam123")
  //     .run(initialPerson)
  // }

  // Macros.resolveTrans[Int]
  // println(builder)
  // Macros.structure(initialPerson.age)

  // val companion = Macros.companion[Person]
  // println(Macros.structure(Person(name = "name", age = 1)))
  // println(Macros.structure(Person("name", 1)))

}
