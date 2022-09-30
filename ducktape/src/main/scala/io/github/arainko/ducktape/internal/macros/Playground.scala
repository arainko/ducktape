package io.github.arainko.ducktape.internal.macros

import io.github.arainko.ducktape.*

case class Person(int: Int, str: String, inside: Inside)
case class Person2(int: Int, str: String, inside: Inside2)

case class Inside(str: String, int: Int)
case class Inside2(int: Int, str: String)

object Playground extends App {
  val cos = Person(1, "2", Inside("2", 1)).to[Person2]

  
  println(cos)

}
