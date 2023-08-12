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

enum Test1 {
  case Cos(int: Nested1)
  case Empty
}

enum Test2 {
  case Cos(int: Nested2)
}

sealed trait Test3

object Test3 {
  case object Empty extends Test3
}

final case class HKT[F[_]](value: F[Int])

final case class Person1(int: Int, str: String, opt: Nested1)
final case class Person2(int: Value, str: String, opt: Nested2)
final case class Nested1(int: Int)
final case class Nested2(int: Int | String, additional: Int) {
  val costam = "asd"
}

final case class Gen[A](int: Int, value: A)

@main def main = {

  // def costam[A](selectors: (Selector ?=> A => Any)*) = ???

  // val sel: Selector = ???

  // given UserDefinedTransformer[Int, String] = _.toString()

  val p = Person1(1, "asd", Nested1(1))

  // given transformer[A, B](using Transformer2[A, B]): Transformer2[Gen[A], Gen[B]] =
  //   src => Interpreter.transformPlanned[Gen[A], Gen[B]](src)

  // No field named 'additional' found in Nested1 @ _.at[Cos].int.additional

  DebugMacros.code {
    println {
      Interpreter.transformPlanned[Test1, Test2](
        Test1.Empty,
        Field2.const(_.at[Test2.Cos].int.additional, 123),
        Case2.const(_.at[Test1.Empty.type], Test2.Cos(Nested2(1, 1)))
        // Field2.const(_.at[Test2.Cos].int, "asd")
      )
    }
  }

  // val cos: Config[Test1, Test2] =
  //   DebugMacros.code {
  //   Case2.const[Test1, Test2, Test1.Empty.type, Test2.Cos](_.at[Test1.Empty.type], Test2.Cos(Nested2(1, 1)))
  //   }

  def cos1[B](f: Selector ?=> Test1 => B): B = ???

  val a = cos1(_.at[Test1.Cos])
  // }

  // Planner.print[Person1, Person2]

  // DebugMacros.code(summon[Transformer2[Gen[Person1], Gen[Person2]]])

  // val builder = AppliedBuilder[Person1, Person2](p)

  // builder.transform(Field2.const(_.opt, Nil))

  // DebugMacros.code {
  //   Interpreter
  //   .transformPlanned[Person1, Person2](
  //     p,
  //     Field2.const(_.opt.additional, 2)
  //   )

  // }

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
