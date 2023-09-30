package io.github.arainko.ducktape

import io.github.arainko.ducktape.internal.macros.DebugMacros
import scala.annotation.nowarn
import scala.deriving.Mirror

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
  case class Cos(int: Nested1) extends Test3
  case object Empty extends Test3
  sealed trait Empty1 extends Test3 

  object Empty1 {
    case object Impl extends Empty1
  }
}

final case class HKT[F[_]](value: F[Int])

final case class Person1(int: Int, str: String, opt: Nested1)
final case class Person2(int: Value, str: String, opt: Nested2)
final case class Nested1(int: Int)
final case class Nested2(int: Int | String, additional: Int) {
  val costam = "asd"
}

final case class Nested3(int: Int | String, additional: Int)

final case class Gen[A](int: Int, value: A)

final case class ProdTest1(test: Test1)
final case class ProdTest2(test: Test2)

@main def main = {

  val p = Person1(1, "asd", Nested1(1))

  /*
  Use cases that I need to support:
    - override a field for which a transformation exists
    - fill in a missing Dest field
    - override a Case for which a transformation exists
    - fill in a missing Source Case
   */
  DebugMacros.code {
    PlanInterpreter.transformBetween[ProdTest1, ProdTest2](
      ProdTest1(Test1.Cos(Nested1(1))),
      // Field2.const(_.test.at[Test2.Cos].int.additional, 1), // missing field
      Field2.computed(_.test.at[Test2.Cos].int.additional, _.test.ordinal + 123),
      Field2.const(_.test.at[Test2.Cos].int.int, 123), // overriden field
      // Field2.const(_.add, 1), // missing field
      Case2.const(_.test.at[Test1.Empty.type], Test2.Cos(Nested2(1, 1))), // missing case
      // Case2.const(_.test.at[Test1.Cos], Test2.Cos(Nested2(1, 1))) // overriden case
    )
  }

  case class PersonCostam(p: PersonFields)
  case class PersonFields(int: Int, str: String)

  PlanInterpreter.transformBetween[Person1, Nested1](
    p, Field2.allMatching(_.int, p)
  )



  def costam(int: Int, str: String): Int = ???

  val aaa: AppliedViaBuilder[Person1, Int, FunctionArguments{val int: Int; val str: String}] =
    Transformer.Debug.showCode {
    AppliedViaBuilder.create(p, costam)
    }

  aaa.transform(Arg2.const(_.int, "aasd"))

  DebugMacros.code {
    PlanInterpreter.transformVia[Person1, Nothing, Nothing](p, costam)
  }

  val lub = if (1 == 2) Test3.Empty else Test3.Empty1.Impl

  summon[Mirror.Of[Test3]]

  DebugMacros.code {
  PlanInterpreter.transformBetween[Test1, Test3](
    Test1.Cos(Nested1(1)),
    Case2.const(_.at[Test1.Empty.type], Test3.Empty1.Impl)
  )
  }

  DebugMacros.structure {
    Arg2.const[Person1, Int, FunctionArguments{ val int: Int; val str: String }, Long, Int](_.int.toLong, 1)
  }

  Configuration.run(Arg2.const[Person1, Int, FunctionArguments{ val int: Int; val str: String }, Int, Int](_.int.at[Int].toLong.toInt, 1))


}
