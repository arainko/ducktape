package io.github.arainko.ducktape.internal.macros

import io.github.arainko.ducktape.*

case class Person(int: Int, str: String, inside: Inside)
case class Person2(int: Int, str: String, inside: Inside2)

case class Inside(str: String, int: Int, inside: EvenMoreInside)
case class Inside2(int: Int, str: String, inside: EvenMoreInside2)

case class EvenMoreInside(str: String, int: Int)
case class EvenMoreInside2(str: String, int: Int)

object Playground extends App {
  val cos = 
    DebugMacros.code {
      Person(1, "2", Inside("2", 1, EvenMoreInside("asd", 3))).to[Person2]
    }

    /*
    to[Person](Person.apply(1, "2", Inside.apply("2", 1, EvenMoreInside.apply("asd", 3))))[Option[Person2]](given_Transformer_Source_Option[Person, Person2](((
      (from: Person) => (
        new Person2(
          int = from.int,
          str = from.str,
          inside = new Inside2(
            int = from.inside.int,
            str = from.inside.str,
            inside = new EvenMoreInside2(
              str = from.inside.inside.str,
              int = from.inside.inside.int
            )
          )
        ): Person2)): ForProduct[Person, Person2])))
    */

    


  def costam(int: Int) = int

  println(cos)

}
