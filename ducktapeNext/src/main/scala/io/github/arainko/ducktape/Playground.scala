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


  // PathMatcher.run[Sum1](_.at[Sum1].at[Sum1.Single.type])

  // DebugMacros.code {
  //   costam[Sum1](
  //     _.at[Sum1.Leaf1].int.at[Int].toByte.toByte.toByte.at[Byte]
  //       // _.at[Sum1.Leaf2].str,
  //   )
  // }

  final case class HKT[F[_]](value: F[Int])

  final case class Person1(int: Int, str: String, opt: Nested1)
  final case class Person2(int: Value, str: String, opt: Nested2)
  final case class Nested1(int: Int)
  final case class Nested2(int: Int | String, additional: Int)

  final case class Gen[A](int: Int, value: A)

  given UserDefinedTransformer[Int, String] = _.toString()

  val p = Person1(1, "asd", Nested1(1))

  given transformer[A, B](using Transformer2[A, B]): Transformer2[Gen[A], Gen[B]] =
    src => Interpreter.transformPlanned[Gen[A], Gen[B]](src)

  // Planner.print[Person1, Person2]

  // DebugMacros.code(summon[Transformer2[Gen[Person1], Gen[Person2]]])

  val builder = AppliedBuilder[Person1, Person2](p)

  builder.transform(Field2.const(_.opt, Nil))

  DebugMacros.code {
    Interpreter
    .transformPlanned[Person1, Person2](
      p,
      Field2.const(_.opt.additional, 2)
      )
  }


  // DebugMacros.code(transformer[Person1, Person2])

  // Interpreter.transformPlanned[Person1, Person2](???)

  // Planner.print[Person1, Person2]

  // Planner.print[Person1, Person2]

  // Transformer.Debug.showCode {
  // Interpreter.transformPlanned[Person1, Person2](p)

  // }
}

final class AppliedBuilder[A, B](value: A) {
  inline def transform(inline config: Config[A, B]*): B = ???
}
