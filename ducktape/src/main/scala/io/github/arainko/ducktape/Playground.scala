package io.github.arainko.ducktape

import io.github.arainko.ducktape.internal.macros.*

object Playground {
  final case class Person(name: String, age: Int)
  final case class Person2(name: String, age: Int)

  final case class Wrapped(int: Int) extends AnyVal

  final case class WrappedG[A](value: A) extends AnyVal

  DebugMacros.structure(Transformer.toAnyVal[Int, Wrapped])

  val p = Person("name", 1)
  // val p2 = Transformer.Debug.showCode(p.transformInto[Person2])
    

}
