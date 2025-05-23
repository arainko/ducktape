package io.github.arainko.ducktape

import io.github.arainko.ducktape.internal.{ FallibleTransformations, TotalTransformations }

extension [Source](source: Source) {

  /**
   * Transforms a Source into a Dest.
   *
   * {{{
   * case class Person(name: String, age: Int)
   * case class ReshuffledPerson(age: Int, name: String)
   *
   * val person: Person = ???
   *
   * person.to[ReshuffledPerson]
   * }}}
   */
  inline def to[Dest]: Dest = TotalTransformations.between[Source, Dest](source, "transformation")

  /**
   * Creates a transformation builder that can be used to configure transformations.
   *
   * {{{
   * case class Person(name: String, age: Int)
   * case class ReshuffledPerson(age: Int, name: String, missingField: Int)
   *
   * val person: Person = ???
   *
   * person.into[ReshuffledPerson].transform(Field.const(_.missingField, 1))
   * }}}
   * @see [[io.github.arainko.ducktape.Field]] and [[io.github.arainko.ducktape.Case]] for all available config options.
   */
  def into[Dest]: AppliedBuilder[Source, Dest] = AppliedBuilder[Source, Dest](source)

  /**
   * Transforms a Source by expanding its fields into arguments of Func.
   *
   * {{{
   * case class Person(name: String, age: Int)
   * def prettyPrintPerson(age: Int, name: String): String = ???
   *
   * val person: Person = ???
   *
   * person.via(prettyPrintPerson)
   * }}}
   * @param function the function to expand (needs to be a method reference)
   */
  transparent inline def via[Func](inline function: Func): Any =
    TotalTransformations.viaInferred[Source, Func](source, function)

  /**
   * Creates a transformation builder for function transformations that can be used to configure these.
   *
   * {{{
   * case class Person(name: String, age: Int)
   * def prettyPrintPerson(age: Int, name: String, missingArg: Int): String = ???
   *
   * val person: Person = ???
   *
   * person.intoVia(prettyPrintPerson).transform(Field.const(_.missingField, 1))
   * }}}
   * @see [[io.github.arainko.ducktape.Field]] and [[io.github.arainko.ducktape.Case]] for all available config options.
   */
  transparent inline def intoVia[Func](inline function: Func): Any =
    AppliedViaBuilder.create(source, function)
}

extension [F[+x], Source](value: Source)(using F: Mode[F]) {

  /**
   * Fallibly transforms a Source into an F[Dest].
   *
   * {{{
   * case class Positive private (value: Int)
   * object Positive {
   *   given Transformer.Fallible[[a] =>> Either[String, a], Int, Positive] =
   *      int => Either.cond(int > 0, Positive(int), s"$int is not positive")
   * }
   * case class PositiveNumbers(one: Positive, two: Positive, three: Positive)
   *
   * Mode.FailFast.either[String].locally {
   *   (1, 2, 3).fallibleTo[PositiveNumbers]
   * }
   * }}}
   */
  inline def fallibleTo[Dest]: F[Dest] = FallibleTransformations.between[F, Source, Dest](value, F, "transformation")

  /**
   * Fallibly transforms a Source by expanding and transforming its fields into arguments Func.
   *
   * {{{
   * case class Positive private (value: Int)
   * object Positive {
   *   given Transformer.Fallible[[a] =>> Either[String, a], Int, Positive] =
   *      int => Either.cond(int > 0, Positive(int), s"$int is not positive")
   * }
   *
   * def calculateWithPositives(one: Positive, two: Positive, three: Positive) = ???
   *
   * Mode.FailFast.either[String].locally {
   *   (1, 2, 3).fallibleVia(calculateWithPositives)
   * }
   * }}}
   * @param function the function to expand (needs to be a method reference)
   */
  transparent inline def fallibleVia[Func](inline function: Func): F[Any] =
    FallibleTransformations.viaInferred[F, Source, Func](value, function, F)
}


object test {
  case class One(int: Int)
  case class Two(int: Int)

  One(1).into[Two].transform(
    Field.modifyDestNames(a => a),
    Field.modifyDestNames(a => a),
    Field.modifyDestNames(a => a),
    Field.modifyDestNames(a => a),
  )
}
