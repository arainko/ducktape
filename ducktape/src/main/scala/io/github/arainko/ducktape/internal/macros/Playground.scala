package io.github.arainko.ducktape.internal.macros

import io.github.arainko.ducktape.Transformer

case class Person(int: Int, str: String)
case class Person2(int: Int, str: String)

object Playground extends App {
  inline def transfromer: Transformer[Person, Person2] = p => new Person2(p.int, p.str)

  val cos = DebugMacros.code {
    DebugMacros.extractTransformer {
      transfromer.transform(Person(1, "2"))
    }
  }

  println(cos)

}
