package io.github.arainko.ducktape

import io.github.arainko.ducktape.internal.macros.DebugMacros
import scala.annotation.nowarn

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

trait Selector {
  extension [A](self: A) def at[B <: A]: B
}

@main def main = {

  def costam[A](selectors: (Selector ?=> A => Any)*) = ???

  val sel: Selector = ???

  // Inlined(
  //   None,
  //   Nil,
  //   TypeApply(
  //     Apply(TypeApply(Select(Ident("sel"), "at"), List(TypeIdent("Sum1"))), List(Select(Ident("Sum1"), "Single"))),
  //     List(TypeSelect(Ident("Sum1"), "Leaf1"))
  //   )
  // )

  // val evidence$1 =
  //   DebugMacros.structure(
  //     sel.at[Sum1](Sum1.Single)[Sum1.Leaf1]
  //   )


  PathMatcher.run[Sum1](_.at[Sum1].at[Sum1.Single.type])

  // DebugMacros.code {
  //   costam[Sum1](
  //     _.at[Sum1.Leaf1].int.at[Int].toByte.toByte.toByte.at[Byte]
  //       // _.at[Sum1.Leaf2].str,
  //   )
  // }

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
  // Interpreter.transformPlanned[Person1, Person2](p)

  // }
}
