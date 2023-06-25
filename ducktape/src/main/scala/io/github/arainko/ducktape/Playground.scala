package io.github.arainko.ducktape

@main def main = {
  final case class Person1(int: Int, str: String, nested: List[Nested1])
  final case class Person2(int: Int, str: String, nested: Vector[Nested2])
  final case class Nested1(int: Int)
  final case class Nested2(int: Int | String)
  val p = Person1(1, "asd", Nested1(1) :: Nil)

  Transformer.Debug.showCode {
  StructTransform.transform[Person1, Person2](p)
  }
}
