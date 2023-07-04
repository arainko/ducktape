package io.github.arainko.ducktape

final case class Value(int: Int) extends AnyVal
final case class ValueGen[A](int: A) extends AnyVal

enum Sum1 {
  case Leaf1(int: Int)
  case Leaf2(str: Int)
  case Single
  case Extra1
  case Extra2
  case Extra3
}

enum Sum2 {
  case Leaf1(int: Int)
  case Leaf2(str: Int, duspko: Int)
  case Single
}

@main def main = {

  final case class HKT[F[_]](value: F[Int])

  final case class Person1(int: Int, str: String, opt: List[Nested1])
  final case class Person2(int: Value, xtra: Int, xtra2: Int, str: String, opt: Vector[Nested2])
  final case class Nested1(int: Int)
  final case class Nested2(int: Int | String, nestedXtra: Int)

  given UserDefinedTransformer[Int, String] = _.toString()

  val p = Person1(1, "asd", Nested1(1) :: Nil)

  // Planner.print[Person1, Person2]
  

  // Planner.print[Person1, Person2]

  // Transformer.Debug.showCode {
    Interpreter.transformPlanned[Person1, Person2](p)

  // }
}
