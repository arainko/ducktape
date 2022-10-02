package io.github.arainko.ducktape.internal.macros

import io.github.arainko.ducktape.*

case class Person(int: Int, str: String, inside: Inside)
case class Person2(int: Int, str: String, inside: Inside2)

case class Inside(str: String, int: Int)
case class Inside2(int: Int, str: String)

object Playground extends App {
  val cos = 
    DebugMacros.code {
      Person(1, "2", Inside("2", 1)).to[Option[Person2]]
    }

  def costam(int: Int) = int

  DebugMacros.structure {
    (p: Person) => costam(costam(p.int))
  }


  


  println(cos)

}
