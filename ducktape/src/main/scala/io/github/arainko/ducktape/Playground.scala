package io.github.arainko.ducktape

@main def main = {
  enum Sum1 {
    case Leaf1(int: Int)
    case Leaf2(str: Int)
    case Single
  }

  enum Sum2 {
    case Leaf1(int: Int)
    case Leaf2(str: Int)
    case Single
  }

  final case class Person1(int: Int, str: String, nested: List[Nested1])
  final case class Person2(int: Int, str: String, nested: Vector[Nested2])
  final case class Nested1(int: Int)
  final case class Nested2(int: Int | String)
  val p = Person1(1, "asd", Nested1(1) :: Nil)

  Transformer.Debug.showCode {
  StructTransform.transform[Sum1, Sum2](Sum1.Single)
  }
}
