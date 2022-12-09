package io.github.arainko.ducktape.internal.macros

import io.github.arainko.ducktape.Transformer

final case class Person(age: Int, name: String)
final case class Person2(age: Int, name: String)

object Playground {
  // DebugMacros.matchTest(

  //   summon[Transformer.ForProduct[Person, Person2]]
  // )
}

