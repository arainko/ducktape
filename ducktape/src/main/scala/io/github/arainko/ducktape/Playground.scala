package io.github.arainko.ducktape

final case class Value(int: Int) extends AnyVal
final case class ValueGen[A](int: A) extends AnyVal

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

  final case class HKT[F[_]](value: F[Int])

  final case class Person1(int: Int, str: String, nested: List[Nested1])
  final case class Person2(int: Int, str: String, nested: Vector[Nested2])
  final case class Nested1(int: Int)
  final case class Nested2(int: Int | String)

  val p = Person1(1, "asd", Nested1(1) :: Nil)

  Planner.print[Sum1, Sum2]
  
  Planner.print[Person1, Person2]

  Transformer.Debug.showCode {
    StructTransform.transform[HKT[List], HKT[Vector]](HKT(Nil))
  }
}
