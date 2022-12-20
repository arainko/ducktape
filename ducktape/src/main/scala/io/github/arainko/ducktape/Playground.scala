package io.github.arainko.ducktape

object Playground {
  final case class Person(name: String, age: Int)
  final case class Person2(name: String, age: Int)

  val p = Person("name", 1)
  val p2 =
    Transformer.Debug.showCode {

      p.transformInto[Person2]
    }

}
