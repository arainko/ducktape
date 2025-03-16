package io.github.arainko.ducktape

import scala.annotation.compileTimeOnly

type Regional[A]

object Regional {
  extension [F[a, b] <: (Case[a, b] | Field[a, b]), A, B, C](self: F[A, B] & Regional[C]) {

    /**
     * Constrains a config option to a certain region (i.e. the option will apply to the transformations 'underneath' the selected field/case):
     *
     * {{{
     * case class Person(name: String, age: Int, info: Person.Info)
     * object Person {
     *   case class Info(accountNo: String, email: String)
     * }
     *
     * case class ReshuffledPerson(age: Int, name: String, extra: Option[String], info: ReshuffledPerson.Info)
     *
     * object ReshuffledPerson {
     *   case class Info(accountNo: String, email: String, extraInfo: Option[String])
     * }
     *
     * val person: Person = Person("Name", 26, Person.Info("123", "email@example.com"))
     * person
     *  .into[ReshuffledPerson]
     *  .transform(
     *    Field.const(_.extra, Some("filled out with const since fallback won't be used here now"))
     *    Field.fallbackToNone.regional(_.info)
     *  )
     * // ReshuffledPerson(26, "Name", Some("filled out with const since fallback won't be used here now"), ReshuffledPerson.Info("123", "email@example.com", None))
     * }}}
     */
    @compileTimeOnly(".regional is only usable as field configuration for transformations")
    def regional[DestFieldTpe](selector: Selector ?=> C => DestFieldTpe): F[A, B] = ???
  }
}

type Local[A]

object Local {
  extension [F[a, b] <: (Case[a, b] | Field[a, b]), A, B, C](self: F[A, B] & Local[C]) {

    /**
     * Constrains a config option to a certain local region (i.e. to a case class/children of an enum 'underneath' the selected field/case):
     *
     * {{{
     * case class Source(int: Int, str: String, level1: SourceLevel1)
     * case class SourceLevel1(INT: Int, STR: String, LEVEL2: SourceLevel2)
     * case class SourceLevel2(int: Int, str: String)
     *
     * case class Dest(int: Int, str: String, level1: DestLevel1)
     * case class DestLevel1(int: Int, str: String, level2: DestLevel2)
     * case class DestLevel2(int: Int, str: String)
     *
     * val source = Source(1, "1", SourceLevel1(2, "2", SourceLevel2(3, "3")))
     * 
     * source
     *  .into[Dest]
     *  .transform(Field.modifyDestNames(_.toUpperCase).local(_.level1)) // <-- we use `.local` to only modify names under `Dest.level1` and not anywhere else
     * // Dest(1, "1", DestLevel1(2, "2", DestLevel2(3, "3")))
     * }}}
     */
    @compileTimeOnly(".local is only usable as field configuration for transformations")
    def local[DestFieldTpe](selector: Selector ?=> C => DestFieldTpe): F[A, B] = ???
  }
}

type TypeSpecific

object TypeSpecific {
  extension [F[a, b] <: (Case[a, b] | Field[a, b]), A, B](self: F[A, B] & TypeSpecific) {
    /**
     * Constrains a config option to a subtypes of the selected type:
     *
     * {{{
     * case class Source(int: Int, str: String, level1: SourceLevel1)
     * case class SourceLevel1(INT: Int, STR: String, LEVEL2: SourceLevel2)
     * case class SourceLevel2(int: Int, str: String)
     *
     * case class Dest(int: Int, str: String, level1: DestLevel1)
     * case class DestLevel1(int: Int, str: String, level2: DestLevel2)
     * case class DestLevel2(int: Int, str: String)
     *
     * val source = Source(1, "1", SourceLevel1(2, "2", SourceLevel2(3, "3")))
     * 
     * source
     *  .into[Dest]
     *  .transform(Field.modifyDestNames(_.toUpperCase).typeSpecific[DestLevel1]) // <-- we use `.typeSpecifc` to only modify names for `DestLevel1` and not anywhere else
     * // Dest(1, "1", DestLevel1(2, "2", DestLevel2(3, "3")))
     * }}}
     */
    @compileTimeOnly(".typeSpecific is only usable as field configuration for transformations")
    def typeSpecific[Tpe]: F[A, B] = ???
  }
}
