package io.github.arainko.ducktape

import scala.annotation.compileTimeOnly

// Kept around for source compat with 0.1.x
@deprecated(message = "Use io.github.arainko.ducktape.Field instead", since = "ducktape 0.2.8")
def Arg: Field.type = Field

opaque type Field[Source, Dest] <: Field.Fallible[Nothing, Source, Dest] = Field.Fallible[Nothing, Source, Dest]

object Field {
  opaque type Fallible[+F[+x], Source, Dest] = Unit

  /**
   * Fills out (or overwrites) a field with a constant value:
   *
   * {{{
   * case class Person(name: String, age: Int)
   * case class ReshuffledPerson(age: Int, name: String, extra: String)
   *
   * val person: Person = Person("Name", 26)
   *
   * person.into[ReshuffledPerson].transform(Field.const(_.extra, "extra"))
   * // ReshuffledPerson(26, "Name", "extra")
   * }}}
   *
   * @param selector path to the configured field
   * @param value the value of the field
   * @see [[io.github.arainko.ducktape.Selector]] for the path selector DSL
   */
  @compileTimeOnly("Field.const is only useable as a field configuration for transformations")
  def const[Source, Dest, DestFieldTpe, ConstTpe](
    selector: Selector ?=> Dest => DestFieldTpe,
    value: ConstTpe
  ): Field[Source, Dest] = ???

  /**
   * Fills out (or overwrites) a field by computing its value from the source value:
   *
   * {{{
   * case class Person(name: String, age: Int)
   * case class ReshuffledPerson(age: Int, name: String, extra: String)
   *
   * val person: Person = Person("Name", 26)
   *
   * person.into[ReshuffledPerson].transform(Field.computed(_.extra, person => person.name + " extra"))
   * // ReshuffledPerson(26, "Name", "Name extra")
   * }}}
   *
   * @param selector path to the configured field
   * @param function the function that computes the value of the field
   * @see [[io.github.arainko.ducktape.Selector]] for the path selector DSL
   */
  @compileTimeOnly("Field.computed is only useable as a field configuration for transformations")
  def computed[Source, Dest, DestFieldTpe, ComputedTpe](
    selector: Selector ?=> Dest => DestFieldTpe,
    function: Source => ComputedTpe
  ): Field[Source, Dest] = ???

  /**
   * Fills out (or overwrites) a field by computing its value from a nested source value.
   *
   * In the following example the type 'Person.Info' is the closest corresponding type of a field to 'info.extraInfo' so
   * it'll be used as an input to the function we provide. Note that we have to explicitly add the type 'Person.Info' to
   * the lambda argument since the compiler is not able to infer this information for us.
   *
   * {{{
   * case class Person(name: String, age: Int, info: Person.Info)
   * object Person {
   *   case class Info(accountNo: String, email: String)
   * }
   *
   * case class ReshuffledPerson(age: Int, name: String, info: ReshuffledPerson.Info)
   * object ReshuffledPerson {
   *   case class Info(accountNo: String, email: String, extraInfo: String)
   * }
   *
   * val person: Person = Person("Name", 26, Person.Info("123", "email@example.com"))
   *
   * person
   *  .into[ReshuffledPerson]
   *  .transform(Field.computedDeep(_.info.extraInfo, (info: Person.Info) => info.email + " extra"))
   * // ReshuffledPerson(26, "Name", ReshuffledPerson.Info("123", "email@example.com", "email@example.com extra"))
   * }}}
   *
   * @param selector path to the configured field
   * @param function the function that computes the value of the field
   * @see [[io.github.arainko.ducktape.Selector]] for the path selector DSL
   */
  @compileTimeOnly("Field.computedDeep is only useable as a field configuration for transformations")
  def computedDeep[Source, Dest, DestFieldTpe, SourceFieldTpe, ComputedTpe](
    selector: Selector ?=> Dest => DestFieldTpe,
    function: SourceFieldTpe => ComputedTpe
  ): Field[Source, Dest] = ???

  /**
   * Alias of 'Field.computed'.
   * @see [[io.github.arainko.ducktape.Field.computed]]
   */
  @compileTimeOnly("Field.renamed is only useable as a field configuration for transformations")
  def renamed[Source, Dest, DestFieldTpe, SourceFieldTpe](
    destSelector: Selector ?=> Dest => DestFieldTpe,
    sourceSelector: Source => SourceFieldTpe
  ): Field[Source, Dest] = ???

  /**
   * Fills out (or overwrites) a field with its default value:
   *
   * {{{
   * case class Person(name: String, age: Int)
   * case class ReshuffledPerson(age: Int, name: String, extra: String = "extra")
   *
   * val person: Person = Person("Name", 26)
   *
   * person.into[ReshuffledPerson].transform(Field.default(_.extra))
   * // ReshuffledPerson(26, "Name", "extra")
   * }}}
   *
   * @param selector path to the configured field
   * @see [[io.github.arainko.ducktape.Selector]] for the path selector DSL
   */
  @compileTimeOnly("Field.default is only useable as a field configuration for transformations")
  def default[Source, Dest, FieldType](selector: Selector ?=> Dest => FieldType): Field[Source, Dest] = ???

  /**
   * Fills out (or overwrites) a field with a fallible constant value:
   *
   * {{{
   * case class Person(name: String, age: Int)
   * case class ReshuffledPerson(age: Int, name: String, extra: String)
   *
   * val person: Person = Person("Name", 26)
   *
   * Mode.FailFast.either[String].locally {
   *   person
   *     .into[ReshuffledPerson]
   *     .fallible
   *     .transform(Field.fallibleConst(_.extra, Right("fallible extra")))
   * }
   * // Right(ReshuffledPerson(26, "Name", "fallible extra"))
   * }}}
   *
   * @param selector path to the configured field
   * @param value the value of the field
   * @see [[io.github.arainko.ducktape.Selector]] for the path selector DSL
   */
  @compileTimeOnly("Field.fallibleConst is only useable as a field configuration for transformations")
  def fallibleConst[F[+x], Source, Dest, DestFieldTpe](
    selector: Selector ?=> Dest => DestFieldTpe,
    value: F[DestFieldTpe]
  ): Field.Fallible[F, Source, Dest] = ???

  /**
   * Fills out (or overwrites) a field by computing its fallible value from the source value:
   *
   * {{{
   * case class Person(name: String, age: Int)
   * case class ReshuffledPerson(age: Int, name: String, extra: String)
   *
   * val person: Person = Person("Name", 26)
   *
   * Mode.FailFast.either[String].locally {
   *   person
   *     .into[ReshuffledPerson]
   *     .fallible
   *     .transform(Field.fallibleComputed(_.extra, person =>  Right(person.name + " fallible extra")))
   * }
   * // Right(ReshuffledPerson(26, "Name", "Name fallible extra"))
   * }}}
   *
   * @param selector path to the configured field
   * @param function the function that computes the value of the field
   * @see [[io.github.arainko.ducktape.Selector]] for the path selector DSL
   */
  @compileTimeOnly("Field.fallibleComputed is only useable as a field configuration for transformations")
  def fallibleComputed[F[+x], Source, Dest, DestFieldTpe](
    selector: Selector ?=> Dest => DestFieldTpe,
    function: Source => F[DestFieldTpe]
  ): Field.Fallible[F, Source, Dest] = ???

  /**
   * Fills out (or overwrites) a field by computing its fallible value from a nested source value.
   *
   * In the following example the type 'Person.Info' is the closest corresponding type of a field to 'info.extraInfo' so
   * it'll be used as an input to the function we provide. Note that we have to explicitly add the type 'Person.Info' to
   * the lambda argument since the compiler is not able to infer this information for us.
   *
   * {{{
   * case class Person(name: String, age: Int, info: Person.Info)
   * object Person {
   *   case class Info(accountNo: String, email: String)
   * }
   *
   * case class ReshuffledPerson(age: Int, name: String, info: ReshuffledPerson.Info)
   * object ReshuffledPerson {
   *   case class Info(accountNo: String, email: String, extraInfo: String)
   * }
   *
   * val person: Person = Person("Name", 26, Person.Info("123", "email@example.com"))
   *
   * Mode.FailFast.either[String].locally {
   *   person
   *     .into[ReshuffledPerson]
   *     .fallible
   *     .transform(Field.fallibleComputedDeep(_.info.extraInfo, (info: Person.Info) => Right(info.email + " fallible extra")))
   * }
   * // Right(ReshuffledPerson(26, "Name", ReshuffledPerson.Info("123", "email@example.com", "email@example.com extra")))
   * }}}
   *
   * @param selector path to the configured field
   * @param function the function that computes the value of the field
   * @see [[io.github.arainko.ducktape.Selector]] for the path selector DSL
   */
  @compileTimeOnly("Field.fallibleComputedDeep is only useable as a field configuration for transformations")
  def fallibleComputedDeep[F[+x], Source, Dest, DestFieldTpe, SourceFieldTpe](
    selector: Selector ?=> Dest => DestFieldTpe,
    function: SourceFieldTpe => F[DestFieldTpe]
  ): Field.Fallible[F, Source, Dest] = ???

  /**
   * Fills out errored-out transformations (for example, when a field is missing) with `None` if the expected type is an Option.
   * This will NOT overwrite `Option` fields that wouldn't generate transformation errors.
   * This works across the whole trnsformation no matter the nesting level.
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
   *
   * person.into[ReshuffledPerson].transform(Field.fallbackToNone)
   * // ReshuffledPerson(26, "Name", None, ReshuffledPerson.Info("123", "email@example.com", None))
   * }}}
   * 
   * To constrain the region of this config option you can call `.regional`:
   * {{{
   * person
   *  .into[ReshuffledPerson]
   *  .transform(
   *    Field.const(_.extra, Some("filled out with const since fallback won't be used here now"))
   *    Field.fallbackToNone.regional(_.info)
   *  )
   * // ReshuffledPerson(26, 
   * //   "Name",
   * //   Some("filled out with const since fallback won't be used here now"), 
   * //   ReshuffledPerson.Info(
   * //     "123", 
   * //     "email@example.com",
   * //     None
   * //   )
   * // )
   * }}}
   *
   * @see [[io.github.arainko.ducktape.Regional]]
   */
  @compileTimeOnly("Field.fallbackToNone is only useable as a field configuration for transformations")
  def fallbackToNone[Source, Dest]: Field[Source, Dest] & Regional[Dest] = ???

  /**
   * Fills out errored-out transformations (for example, when a field is missing) with their respective defaults.
   * This will NOT overwrite fields that wouldn't generate transformation errors.
   * This works across the whole transformation no matter the nesting level.
   * 
   * {{{
   * case class Person(name: String, age: Int, info: Person.Info)
   * object Person {
   *   case class Info(accountNo: String, email: String)
   * }
   * 
   * case class ReshuffledPerson(age: Int, name: String, extra: String = "default extra", info: ReshuffledPerson.Info)
   * 
   * object ReshuffledPerson {
   *   case class Info(accountNo: String, email: String, extraInfo = "default extra info")
   * }
   *
   * val person: Person = Person("Name", 26, Person.Info("123", "email@example.com"))
   *
   * person.into[ReshuffledPerson].transform(Field.fallbackToDefault)
   * // ReshuffledPerson(26, "Name", "default extra", ReshuffledPerson.Info("123", "email@example.com", "default extra info"))
   * }}}
   * 
   * To constrain the region of this config option you can call `.regional`:
   * {{{
   * person
   *  .into[ReshuffledPerson]
   *  .transform(
   *    Field.const(_.extra, "filled out with const since fallback won't be used here now")
   *    Field.fallbackToDefault.regional(_.info)
   *  )
   * // ReshuffledPerson(26, 
   * //   "Name",
   * //   "filled out with const since fallback won't be used here now", 
   * //   ReshuffledPerson.Info(
   * //     "123", 
   * //     "email@example.com",
   * //     "default extra info"  
   * //   )
   * // )
   * }}}
   *
   * @see [[io.github.arainko.ducktape.Regional]]
   */
  @compileTimeOnly("Field.fallbackToDefault is only useable as a field configuration for transformations")
  def fallbackToDefault[Source, Dest]: Field[Source, Dest] & Regional[Dest] = ???

  @compileTimeOnly("Field.allMatching is only useable as a field configuration for transformations")
  def allMatching[Source, Dest, DestFieldTpe, ProductTpe](
    selector: Selector ?=> Dest => DestFieldTpe,
    product: ProductTpe
  ): Field[Source, Dest] =
    ???

  @compileTimeOnly("Field.allMatching is only useable as a field configuration for transformations")
  def allMatching[Source, Dest, ProductTpe](product: ProductTpe): Field[Source, Dest] =
    ???

  @compileTimeOnly("Field.modifyDestNames is only useable as a field configuration for transformations")
  def modifyDestNames[Source, Dest](renamer: Renamer => Renamer): Field[Source, Dest] & Regional[Dest] & Local[Dest] &
    TypeSpecific = ???

  @compileTimeOnly("Field.modifySourceNames is only useable as a field configuration for transformations")
  def modifySourceNames[Source, Dest](renamer: Renamer => Renamer): Field[Source, Dest] & Regional[Source] & Local[Source] &
    TypeSpecific = ???
}

object a extends App {
  case class Person(name: String, age: Int)
  case class ReshuffledPerson(age: Int, name: String, extra: String)

  val person: Person = Person("Name", 18)

  println {
    Mode.FailFast.either[String].locally {
      person
        .into[ReshuffledPerson]
        .fallible
        .transform(Field.fallibleComputed(_.extra, person => Right(person.name + " fallible extra")))
    }
  }

  // ReshuffledPerson(18, "Name", "extra")
}
