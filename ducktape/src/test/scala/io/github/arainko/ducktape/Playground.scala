package io.github.arainko.ducktape

import io.github.arainko.ducktape.fallible.{ Accumulating, FailFast }
import io.github.arainko.ducktape.internal.*
import io.github.arainko.ducktape.internal.macros.*
import io.github.arainko.ducktape.newtypes.{ Age, Name }

import scala.deriving.Mirror

final case class Person(name: String, age: Int, additional: Int, p: Person2)

final case class Person2(name: String, age: Int, additional: Int, int: Int)

final case class Person3(name: String, age: Int, additional: newtypes.Age, int: newtypes.Age)

final case class PersonRefined(name: newtypes.Name, age: Int, additional: Option[newtypes.Age], int: Int, p: Person3)

object newtypes {
  opaque type Name = String

  object Name {
    given refineNameFf: FailFast[Option, String, Name] = Some(_)
    given refineNameAcc: Accumulating[[A] =>> Either[::[String], A], String, Name] =
      a => Left(::("WOOPS", Nil))
  }

  opaque type Age = Int

  object Age {
    given refineAgeFf: FailFast[Option, Int, Age] = _ => None
    given refineAgeAcc: Accumulating[[A] =>> Either[::[String], A], Int, Age] = a => Left(::("WOOPS2", Nil))
  }
}

object Playground extends App {
  val person = Person("name", 1, 1, Person2("name", 1, 1, 1))

  type EitherCons[A] = Either[::[String], A]

  DebugMacros.code {
    person
      .intoVia(PersonRefined.apply)
      .failFast[Option]
      .transform(
        // Arg.const(_.int, 1),
        Arg.fallibleConst(_.int, None)
      )
  }
}
