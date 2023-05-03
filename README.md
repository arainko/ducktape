# ![ducktape-logo-32](https://user-images.githubusercontent.com/46346508/236060869-3b118075-f660-44c9-9d0d-d40fba5c8db0.svg) ducktape

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.github.arainko/ducktape_3/badge.svg?style=flat-square)](https://maven-badges.herokuapp.com/maven-central/io.github.arainko/ducktape_3)

*ducktape* is a library for boilerplate-less and configurable transformations between case classes and enums/sealed traits for Scala 3. Directly inspired by [chimney](https://github.com/scalalandio/chimney).

If this project interests you, please drop a 🌟 - these things are worthless but give me a dopamine rush nonetheless.

### Installation
```scala
libraryDependencies += "io.github.arainko" %% "ducktape" % "0.1.7"

// or if you're using Scala.js or Scala Native
libraryDependencies += "io.github.arainko" %%% "ducktape" % "0.1.7"
```

NOTE: the [version scheme](https://www.scala-lang.org/blog/2021/02/16/preventing-version-conflicts-with-versionscheme.html) is set to `early-semver`

### Total transformations - examples

#### 1. *Case class to case class*

```scala
import io.github.arainko.ducktape.*

final case class Person(firstName: String, lastName: String, age: Int)
final case class PersonButMoreFields(firstName: String, lastName: String, age: Int, socialSecurityNo: String)

val personWithMoreFields = PersonButMoreFields("John", "Doe", 30, "SOCIAL-NUM-12345")
// personWithMoreFields: PersonButMoreFields = PersonButMoreFields(
//   firstName = "John",
//   lastName = "Doe",
//   age = 30,
//   socialSecurityNo = "SOCIAL-NUM-12345"
// )

val transformed = personWithMoreFields.to[Person]
// transformed: Person = Person(firstName = "John", lastName = "Doe", age = 30)
```

Automatic case class to case class transformations are supported given that
the source type has all the fields of the destination type and the types corresponding to these fields have an instance of `Transformer` in scope.

If these requirements are not met, a compiletime error is issued:
```scala
val person = Person("Jerry", "Smith", 20)

person.to[PersonButMoreFields]

// error:
// No field named 'socialSecurityNo' found in Person
//     .into[Person2]
//                   ^
```

#### 2. *Enum to enum*

```scala
import io.github.arainko.ducktape.*

enum Size:
  case Small, Medium, Large

enum ExtraSize:
  case ExtraSmall, Small, Medium, Large, ExtraLarge

val transformed = Size.Small.to[ExtraSize]
// transformed: ExtraSize = Small
```

We can't go to a coproduct that doesn't contain all of our cases (name wise):

```scala
val size = ExtraSize.Small.to[Size]
// error:
// No child named 'ExtraSmall' in Size
```

Automatic enum to enum transformations are supported given that the destination enum contains a subset of cases
we want to transform into, otherwise a compiletime errors is issued.

#### 3. *Case class to case class with config*

As we established earlier, going from `Person` to `PersonButMoreFields` cannot happen automatically as the former
doesn't have the `socialSecurityNo` field, but it has all the other fields - so it's almost there, we just have to nudge it a lil' bit.

We can do so with field configurations in 3 ways:
  1. Set a constant to a specific field with `Field.const`
  2. Compute the value for a specific field by applying a function with `Field.computed`
  3. Use a different field in its place - 'rename' it with `Field.renamed`
  4. Use the default value of the target case class with `Field.default`
  5. Grab all matching fields from another case class with `Field.allMatching`

```scala
import io.github.arainko.ducktape.*

final case class Person(firstName: String, lastName: String, age: Int)
final case class PersonButMoreFields(firstName: String, lastName: String, age: Int, socialSecurityNo: String = "ssn")

val person = Person("Jerry", "Smith", 20)
// person: Person = Person(firstName = "Jerry", lastName = "Smith", age = 20)

// 1. Set a constant to a specific field
val withConstant = 
  person
    .into[PersonButMoreFields]
    .transform(Field.const(_.socialSecurityNo, "CONSTANT-SSN"))
// withConstant: PersonButMoreFields = PersonButMoreFields(
//   firstName = "Jerry",
//   lastName = "Smith",
//   age = 20,
//   socialSecurityNo = "CONSTANT-SSN"
// )

// 2. Compute the value for a specific field by applying a function
val withComputed = 
  person
    .into[PersonButMoreFields]
    .transform(Field.computed(_.socialSecurityNo, p => s"${p.firstName}-COMPUTED-SSN"))
// withComputed: PersonButMoreFields = PersonButMoreFields(
//   firstName = "Jerry",
//   lastName = "Smith",
//   age = 20,
//   socialSecurityNo = "Jerry-COMPUTED-SSN"
// )

// 3. Use a different field in its place - 'rename' it
val withRename = 
  person
    .into[PersonButMoreFields]
    .transform(Field.renamed(_.socialSecurityNo, _.firstName))
// withRename: PersonButMoreFields = PersonButMoreFields(
//   firstName = "Jerry",
//   lastName = "Smith",
//   age = 20,
//   socialSecurityNo = "Jerry"
// )

// 4. Use the default value of a specific field (a compiletime error will be issued if the field doesn't have a default)
val withDefault = 
  person
    .into[PersonButMoreFields]
    .transform(Field.default(_.socialSecurityNo))
// withDefault: PersonButMoreFields = PersonButMoreFields(
//   firstName = "Jerry",
//   lastName = "Smith",
//   age = 20,
//   socialSecurityNo = "ssn"
// )

final case class FieldSource(lastName: String, socialSecurityNo: String)

// 5. Grab and use all matching fields from a different case class (a compiletime error will be issued if none of the fields match)
val withAllMatchingFields = 
  person
    .into[PersonButMoreFields]
    .transform(Field.allMatching(FieldSource("SourcedLastName", "SOURCED-SSN")))
// withAllMatchingFields: PersonButMoreFields = PersonButMoreFields(
//   firstName = "Jerry",
//   lastName = "SourcedLastName",
//   age = 20,
//   socialSecurityNo = "SOURCED-SSN"
// )
```

In case we repeatedly apply configurations to the same field a warning is emitted (which can be ignored with `@nowarn`) and the latest one is chosen:

```scala
val withRepeatedConfig =
  person
    .into[PersonButMoreFields]
    .transform(
      Field.renamed(_.socialSecurityNo, _.firstName),
      Field.computed(_.socialSecurityNo, p => s"${p.firstName}-COMPUTED-SSN"),
      Field.allMatching(FieldSource("SourcedLastName", "SOURCED-SSN")),
      Field.const(_.socialSecurityNo, "CONSTANT-SSN")
    )
// warning: 
//  Field 'socialSecurityNo' is configured multiple times
//  
//  If this is desired you can ignore this warning with @nowarn(msg=Field 'socialSecurityNo' is configured multiple times)
//
```

Of course we can use this to override the automatic derivation for each field:

```scala
val withEverythingOverriden = 
  person
    .into[PersonButMoreFields]
    .transform(
      Field.const(_.socialSecurityNo, "CONSTANT-SSN"),
      Field.const(_.age, 100),
      Field.const(_.firstName, "OVERRIDEN-FIRST-NAME"),
      Field.const(_.lastName, "OVERRIDEN-LAST-NAME"),
    )
// withEverythingOverriden: PersonButMoreFields = PersonButMoreFields(
//   firstName = "OVERRIDEN-FIRST-NAME",
//   lastName = "OVERRIDEN-LAST-NAME",
//   age = 100,
//   socialSecurityNo = "CONSTANT-SSN"
// )
```

#### 4. Enum to enum with config

Enum transformations, just like case class transformations, can be configured by:
* supplying a constant value with `Case.const`,
* supplying a function that will be applied to the chosen subtype with `Case.computed`.

```scala
import io.github.arainko.ducktape.*

enum Size:
  case Small, Medium, Large

enum ExtraSize:
  case ExtraSmall, Small, Medium, Large, ExtraLarge

// Specify a constant for the cases that are not covered automatically
val withConstants = 
  ExtraSize.ExtraSmall
    .into[Size]
    .transform(
      Case.const[ExtraSize.ExtraSmall.type](Size.Small),
      Case.const[ExtraSize.ExtraLarge.type](Size.Large)
    )
// withConstants: Size = Small

// Specify a function to transform a given case with that function
val withComputed =
  ExtraSize.ExtraSmall
    .into[Size]
    .transform(
      Case.computed[ExtraSize.ExtraSmall.type](_ => Size.Small),
      Case.computed[ExtraSize.ExtraLarge.type](_ => Size.Large)
    )
// withComputed: Size = Small
```

#### 5. Method to case class

We can also let `ducktape` expand method incovations for us:

```scala
import io.github.arainko.ducktape.*

final case class Person1(firstName: String, lastName: String, age: Int)
final case class Person2(firstName: String, lastName: String, age: Int)

def methodToExpand(lastName: String, age: Int, firstName: String): Person2 =
  Person2(firstName, lastName, age)

val person1: Person1 = Person1("John", "Doe", 23)
// person1: Person1 = Person1(firstName = "John", lastName = "Doe", age = 23)
val person2: Person2 = person1.via(methodToExpand)
// person2: Person2 = Person2(firstName = "John", lastName = "Doe", age = 23)
```

In this case, `ducktape` will match the fields from `Person` to parameter names of `methodToExpand` failing at compiletime if
a parameter cannot be matched (be it there's no name correspondence or a `Transformer` between types of two fields with the same name isn't available):

```scala
def methodToExpandButOneMoreArg(lastName: String, age: Int, firstName: String, additionalArg: String): Person2 =
  Person2(firstName + additionalArg, lastName, age)

person1.via(methodToExpandButOneMoreArg)
// error:
// No field named 'additionalArg' in Person
```

#### 6. Method to case class with config

Just like transforming between case classes and coproducts we can nudge the derivation in some places to complete the puzzle, let's
tackle the last example once again:

```scala
def methodToExpandButOneMoreArg(lastName: String, age: Int, firstName: String, additionalArg: String): Person2 =
  Person2(firstName + additionalArg, lastName, age)

val withConstant = 
  person1
    .intoVia(methodToExpandButOneMoreArg)
    .transform(Arg.const(_.additionalArg, "-CONST ARG"))
// withConstant: Person2 = Person2(
//   firstName = "John-CONST ARG",
//   lastName = "Doe",
//   age = 23
// )

val withComputed = 
  person1
    .intoVia(methodToExpandButOneMoreArg)
    .transform(Arg.computed(_.additionalArg, _.lastName + "-COMPUTED"))
// withComputed: Person2 = Person2(
//   firstName = "JohnDoe-COMPUTED",
//   lastName = "Doe",
//   age = 23
// )

val withRenamed = 
  person1
    .intoVia(methodToExpandButOneMoreArg)
    .transform(Arg.renamed(_.additionalArg, _.lastName))
// withRenamed: Person2 = Person2(
//   firstName = "JohnDoe",
//   lastName = "Doe",
//   age = 23
// )
```

#### 7. Automatic wrapping and unwrapping of `AnyVal`

Despite being a really flawed abstraction `AnyVal` is pretty prevalent in Scala 2 code that you may want to interop with
and `ducktape` is here to assist you. `Transformer` definitions for wrapping and uwrapping `AnyVals` are
automatically available:

```scala
import io.github.arainko.ducktape.*

final case class WrappedString(value: String) extends AnyVal

val wrapped = WrappedString("I am a String")
// wrapped: WrappedString = WrappedString(value = "I am a String")

val unwrapped = wrapped.to[String]
// unwrapped: String = "I am a String"

val wrappedAgain = unwrapped.to[WrappedString]
// wrappedAgain: WrappedString = WrappedString(value = "I am a String")
```

#### 8. Defining custom `Transformers`

If for some reason you need a custom `Transformer` in scope but still want to partially rely
on the automatic derivation and have all the configuration DSL goodies you can use these:

* `Transformer.define[Source, Dest].build(<Field/Case configuration>)`
* `Transformer.defineVia[Source](someMethod).build(<Arg configuration>)`
  
Examples:

```scala
import io.github.arainko.ducktape.*

final case class TestClass(str: String, int: Int)
final case class TestClassWithAdditionalList(int: Int, str: String, additionalArg: List[String])

def method(str: String, int: Int, additionalArg: List[String]) = TestClassWithAdditionalList(int, str, additionalArg)

val testClass = TestClass("str", 1)
// testClass: TestClass = TestClass(str = "str", int = 1)

val definedViaTransformer =
  Transformer
    .defineVia[TestClass](method)
    .build(Arg.const(_.additionalArg, List("const")))
// definedViaTransformer: Transformer[TestClass, TestClassWithAdditionalList] = repl.MdocSession$MdocApp7$$Lambda$14740/0x00000001038ae040@1aa3f2e7

val definedTransformer =
  Transformer
    .define[TestClass, TestClassWithAdditionalList]   
    .build(Field.const(_.additionalArg, List("const")))
// definedTransformer: Transformer[TestClass, TestClassWithAdditionalList] = repl.MdocSession$MdocApp7$$Lambda$14741/0x00000001038ae440@ef9fbf9

val transformedVia = definedViaTransformer.transform(testClass)
// transformedVia: TestClassWithAdditionalList = TestClassWithAdditionalList(
//   int = 1,
//   str = "str",
//   additionalArg = List("const")
// )

val transformed = definedTransformer.transform(testClass)
// transformed: TestClassWithAdditionalList = TestClassWithAdditionalList(
//   int = 1,
//   str = "str",
//   additionalArg = List("const")
// )
```

#### Usecase: recursive `Transformers`

Recursive instances are lazy by nature so automatic derivation will be of no use here, we need to get our hands a little bit dirty:

```scala
import io.github.arainko.ducktape.*

final case class Rec[A](value: A, rec: Option[Rec[A]])

given recursive[A, B](using Transformer[A, B]): Transformer[Rec[A], Rec[B]] = 
  Transformer.define[Rec[A], Rec[B]].build()

Rec("1", Some(Rec("2", Some(Rec("3", None))))).to[Rec[Option[String]]]
// res9: Rec[Option[String]] = Rec(
//   value = Some(value = "1"),
//   rec = Some(
//     value = Rec(
//       value = Some(value = "2"),
//       rec = Some(value = Rec(value = Some(value = "3"), rec = None))
//     )
//   )
// )
```

### Fallible transfomations - examples
Sometimes ordinary field mappings just do not cut it, more often than not our domain model's constructors are hidden behind a safe factory method, eg.:

```scala
import io.github.arainko.ducktape.*

final case class ValidatedPerson private (name: String, age: Int)

object ValidatedPerson {
  def create(name: String, age: Int): Either[String, ValidatedPerson] =
    for {
      validatedName <- Either.cond(!name.isBlank, name, "Name should not be blank")
      validatedAge  <- Either.cond(age > 0, age, "Age should be positive")
    } yield ValidatedPerson(validatedName, validatedAge)
}
```

The `via` method expansion mechanism has us covered in the most straight-forward of use cases where there are no nested fallible transformations:

```scala
final case class UnvalidatedPerson(name: String, age: Int, socialSecurityNo: String)

val unvalidatedPerson = UnvalidatedPerson("ValidName", -1, "SSN")
// unvalidatedPerson: UnvalidatedPerson = UnvalidatedPerson(
//   name = "ValidName",
//   age = -1,
//   socialSecurityNo = "SSN"
// )

val transformed = unvalidatedPerson.via(ValidatedPerson.create)
// transformed: Either[String, ValidatedPerson] = Left(
//   value = "Age should be positive"
// )
```

But this quickly falls apart when nested transformations are introduced and we're pretty much back to square one where we're on our own to write the boilerplate.

That's where `Fallible Transformers` and their modes come in: 
* `Transformer.Mode.Accumulating` for error accumulation,
* `Transformer.Mode.FailFast` for the cases where we just want to bail at the very first sight of trouble.

Let's look at the definition of all of these:

#### Definition of `FallibleTransformer` aka `Transformer.Fallible` and `Transformer.Mode`

```scala
trait FallibleTransformer[F[+x], Source, Dest] {
  def transform(value: Source): F[Dest]
}
```
So a `Fallible` transformer takes a `Source` and gives back a `Dest` wrapped in an `F` where `F` is the wrapper type for our transformations eg. if `F[+x]` = `Either[List[String], x]` then the `transform` method will return an `Either[List[String], Dest]`.

```scala
sealed trait Mode[F[+x]] {
  def pure[A](value: A): F[A]
  def map[A, B](fa: F[A], f: A => B): F[B]
  def traverseCollection[A, B, AColl[x] <: Iterable[x], BColl[x] <: Iterable[x]](collection: AColl[A])(using
    transformer: FallibleTransformer[F, A, B],
    factory: Factory[B, BColl[B]]
  ): F[BColl[B]]
}
```

Moving on to `Transformer.Mode`, what exactly is it and why do we need it? So a `Mode[F]` is typeclass that gives us two bits of information:
* a hint for the derivation mechanism which transformation mode to use (hence the name!)
* some operations on the abstract `F` wrapper type, namely:
  * `pure` is for wrapping arbitrary values into `F`, eg. if `F[+x] = Either[List[String], x]` then calling `pure` would involve just wrapping the value in a `Right.apply` call.
  * `map` is for operating on the wrapped values, eg. if we find ourselves with a `F[Int]` in hand and we want to transform the value 'inside' to a `String` we can call `.map(_.toString)` to yield a `F[String]`
  * `traverseCollection` is for the cases where we end up with eg. a `List[F[String]]` and we want to transform that into a `F[List[String]]` according to the rules of the `F` type wrapper and not blow up the stack in the process

As mentioned earlier, `Modes` come in two flavors - one for error accumulating transformations (`Transformer.Mode.Accumulating[F]`) and one for fail fast transformations (`Transformer.Mode.FailFast[F]`):

```scala
object Mode {
  trait Accumulating[F[+x]] extends Mode[F] {
    def product[A, B](fa: F[A], fb: F[B]): F[(A, B)]
  }

  trait FailFast[F[+x]] extends Mode[F] {
    def flatMap[A, B](fa: F[A], f: A => F[B]): F[B]
  }
}
```

Each one of these exposes one operation that dictates its approach to errors, `flatMap` entails a dependency between fallible transformations so if we chain multiple `flatMaps` together our transformation will stop at the very first error, contrary to this `Transformer.Mode.Accumulating` exposes a `product` operation that given two independent transformations wrapped in `F` gives us back a tuple wrapped in an `F`, what that means is that each one of the transformations is independent from each other so we're able to accumulate all of the errors produced by these.

For accumulating transformations `ducktape` provides instances for `Either` with any subtype of `Iterable` on the left side, so that eg. `Transformer.Mode.Accumulating[[A] =>> Either[List[String], A]]` is available out of the box.

For fail fast transformations instances for `Option` and `Either` are avaiable out of the box.

#### Automatic fallible transformations

Now for the meat and potatoes of `Fallible Transformers`. To make use of the derivation mechanism that `ducktape` provides we should strive for our model to be modeled in a specific way - with a new nominal type per each validated field, which comes down to... Newtypes!

Let's define a minimalist newtype abstraction that will also do validation (this is a one-time effort that can easily be extracted to a library):

```scala
abstract class NewtypeValidated[A](pred: A => Boolean, errorMessage: String) {
  opaque type Type = A

  protected def unsafe(value: A): Type = value

  def make(value: A): Either[String, Type] = Either.cond(pred(value), value, errorMessage)

  def makeAccumulating(value: A): Either[List[String], Type] = 
    make(value).left.map(_ :: Nil)

  extension (self: Type) {
    def value: A = self
  }

  // these instances will be available in the implicit scope of `Type` (that is, our newtype)
  given accumulatingWrappingTransformer: Transformer.Fallible[[a] =>> Either[List[String], a], A, Type] = makeAccumulating(_)

  given failFastWrappingTransformer: Transformer.Fallible[[a] =>> Either[String, a], A, Type] = make(_)

  given unwrappingTransformer: Transformer[Type, A] = _.value

}
```

Now let's get back to the definition of `ValidatedPerson` and tweak it a little:

```scala
final case class ValidatedPerson(name: ValidatedPerson.Name, age: ValidatedPerson.Age, socialSecurityNo: ValidatedPerson.SSN)

object ValidatedPerson {
  object Name extends NewtypeValidated[String](str => !str.isBlank, "Name should not be blank!")
  export Name.Type as Name

  object Age extends NewtypeValidated[Int](int => int > 0, "Age should be positive!")
  export Age.Type as Age

  object SSN extends NewtypeValidated[String](str => str.length > 5, "SSN should be longer than 5!")
  export SSN.Type as SSN

}
```

We introduce a newtype for each field, this way we can keep our invariants at compiletime and also let `ducktape` do its thing.

```scala
// this should trip up our validation
val bad = UnvalidatedPerson(name = "", age = -1, socialSecurityNo = "SOCIALNO")
// bad: UnvalidatedPerson = UnvalidatedPerson(
//   name = "",
//   age = -1,
//   socialSecurityNo = "SOCIALNO"
// )

// this one should pass
val good = UnvalidatedPerson(name = "ValidName", age = 24, socialSecurityNo = "SOCIALNO")
// good: UnvalidatedPerson = UnvalidatedPerson(
//   name = "ValidName",
//   age = 24,
//   socialSecurityNo = "SOCIALNO"
// )
```

Instances of `Transformer.Fallible` wrapped in some type `F` are derived automatically for case classes given that a `Transformer.Mode.Accumulating` instance exists for `F` and all of the fields of the source type have a corresponding counterpart in the destination type and each one of them has an instance of either `Transformer.Fallible` or a total `Transformer` in scope.

```scala
given Transformer.Mode.Accumulating[[A] =>> Either[List[String], A]] = 
  Transformer.Mode.Accumulating.either[String, List]

bad.fallibleTo[ValidatedPerson]
// res11: Either[List[String], ValidatedPerson] = Left(
//   value = List("Name should not be blank!", "Age should be positive!")
// )
good.fallibleTo[ValidatedPerson]
// res12: Either[List[String], ValidatedPerson] = Right(
//   value = ValidatedPerson(
//     name = "ValidName",
//     age = 24,
//     socialSecurityNo = "SOCIALNO"
//   )
// )
```

and the generated code looks like this:

``` scala 
  {
    val transformer$proxy3
      : FallibleTransformer[[A >: Nothing <: Any] =>> Either[List[String], A], UnvalidatedPerson, ValidatedPerson] = {
      val Dest$proxy3: Product {
        type MirroredMonoType >: ValidatedPerson <: ValidatedPerson
        type MirroredType >: ValidatedPerson <: ValidatedPerson
        type MirroredLabel >: "ValidatedPerson" <: "ValidatedPerson"
        type MirroredElemTypes >: *:[Name, *:[Age, *:[SSN, EmptyTuple]]] <: *:[Name, *:[Age, *:[SSN, EmptyTuple]]]
        type MirroredElemLabels >: *:["name", *:["age", *:["socialSecurityNo", EmptyTuple]]] <: *:[
          "name",
          *:["age", *:["socialSecurityNo", EmptyTuple]]
        ]
      } = ValidatedPerson.$asInstanceOf$[
        Product {
          type MirroredMonoType >: ValidatedPerson <: ValidatedPerson
          type MirroredType >: ValidatedPerson <: ValidatedPerson
          type MirroredLabel >: "ValidatedPerson" <: "ValidatedPerson"
          type MirroredElemTypes >: *:[Name, *:[Age, *:[SSN, EmptyTuple]]] <: *:[Name, *:[Age, *:[SSN, EmptyTuple]]]
          type MirroredElemLabels >: *:["name", *:["age", *:["socialSecurityNo", EmptyTuple]]] <: *:[
            "name",
            *:["age", *:["socialSecurityNo", EmptyTuple]]
          ]
        }
      ]

      ((
        (source: UnvalidatedPerson) =>
          given_Accumulating_Either.map[Tuple2[Tuple2[Type, Type], Type], ValidatedPerson](
            given_Accumulating_Either.product[Tuple2[Type, Type], Type](
              given_Accumulating_Either.product[Type, Type](
                ValidatedPerson.Name.accumulatingWrappingTransformer.transform(source.name),
                ValidatedPerson.Age.accumulatingWrappingTransformer.transform(source.age)
              ),
              ValidatedPerson.SSN.accumulatingWrappingTransformer.transform(source.socialSecurityNo)
            ),
            (value: Tuple2[Tuple2[Type, Type], Type]) =>
              value match {
                case Tuple2(Tuple2(name, age), socialSecurityNo) =>
                  new ValidatedPerson(name = name, age = age, socialSecurityNo = socialSecurityNo)
                case x =>
                  throw new MatchError(x)
              }
          )
      ): FallibleTransformer[
        [A >: Nothing <: Any] =>> Either[List[String], A],
        UnvalidatedPerson,
        ValidatedPerson
      ]): FallibleTransformer[[A >: Nothing <: Any] =>> Either[List[String], A], UnvalidatedPerson, ValidatedPerson]
    }

    transformer$proxy3.transform(bad): Either[List[String], ValidatedPerson]
  }
```

Same goes for instances that do fail fast transformations (you need `Transformer.Mode.FailFast[F]` in scope in this case)

```scala
given Transformer.Mode.FailFast[[A] =>> Either[String, A]] = 
  Transformer.Mode.FailFast.either[String]

bad.fallibleTo[ValidatedPerson]
// res14: Either[String, ValidatedPerson] = Left(
//   value = "Name should not be blank!"
// )
good.fallibleTo[ValidatedPerson]
// res15: Either[String, ValidatedPerson] = Right(
//   value = ValidatedPerson(
//     name = "ValidName",
//     age = 24,
//     socialSecurityNo = "SOCIALNO"
//   )
// )
```

and the generated code looks like this:
``` scala 
  {
    val transformer$proxy6
      : FallibleTransformer[[A >: Nothing <: Any] =>> Either[String, A], UnvalidatedPerson, ValidatedPerson] = {
      val Dest$proxy6: Product {
        type MirroredMonoType >: ValidatedPerson <: ValidatedPerson
        type MirroredType >: ValidatedPerson <: ValidatedPerson
        type MirroredLabel >: "ValidatedPerson" <: "ValidatedPerson"
        type MirroredElemTypes >: *:[Name, *:[Age, *:[SSN, EmptyTuple]]] <: *:[Name, *:[Age, *:[SSN, EmptyTuple]]]
        type MirroredElemLabels >: *:["name", *:["age", *:["socialSecurityNo", EmptyTuple]]] <: *:[
          "name",
          *:["age", *:["socialSecurityNo", EmptyTuple]]
        ]
      } = ValidatedPerson.$asInstanceOf$[
        Product {
          type MirroredMonoType >: ValidatedPerson <: ValidatedPerson
          type MirroredType >: ValidatedPerson <: ValidatedPerson
          type MirroredLabel >: "ValidatedPerson" <: "ValidatedPerson"
          type MirroredElemTypes >: *:[Name, *:[Age, *:[SSN, EmptyTuple]]] <: *:[Name, *:[Age, *:[SSN, EmptyTuple]]]
          type MirroredElemLabels >: *:["name", *:["age", *:["socialSecurityNo", EmptyTuple]]] <: *:[
            "name",
            *:["age", *:["socialSecurityNo", EmptyTuple]]
          ]
        }
      ]

      ((
        (source: UnvalidatedPerson) =>
          given_FailFast_Either.flatMap[Type, ValidatedPerson](
            ValidatedPerson.Name.failFastWrappingTransformer.transform(source.name),
            (name: Type) =>
              given_FailFast_Either.flatMap[Type, ValidatedPerson](
                ValidatedPerson.Age.failFastWrappingTransformer.transform(source.age),
                (age: Type) =>
                  given_FailFast_Either.map[Type, ValidatedPerson](
                    ValidatedPerson.SSN.failFastWrappingTransformer.transform(source.socialSecurityNo),
                    (socialSecurityNo: Type) => new ValidatedPerson(name = name, age = age, socialSecurityNo = socialSecurityNo)
                  )
              )
          )
      ): FallibleTransformer[
        [A >: Nothing <: Any] =>> Either[String, A],
        UnvalidatedPerson,
        ValidatedPerson
      ]): FallibleTransformer[[A >: Nothing <: Any] =>> Either[String, A], UnvalidatedPerson, ValidatedPerson]
    }

    transformer$proxy6.transform(bad): Either[String, ValidatedPerson]
  }
```

#### Configured fallible transformations
Fallible transformations support a superset of total transformations' configuration options.

##### `Field` config
All of these work the very same way they do in total transformations:
* `Field.const`
* `Field.computed`
* `Field.renamed`
* `Field.allMatching`
* `Field.default`

plus two fallible-specific config options:
* `Field.fallibleConst`
* `Field.fallibleComputed`

which work like so for `Accumulating` transformations:
```scala
given Transformer.Mode.Accumulating[[A] =>> Either[List[String], A]] = 
  Transformer.Mode.Accumulating.either[String, List]

bad
  .into[ValidatedPerson]
  .fallible
  .transform(
    Field.fallibleConst(_.name, ValidatedPerson.Name.makeAccumulating("ConstValidName")),
    Field.fallibleComputed(_.age, unvPerson => ValidatedPerson.Age.makeAccumulating(unvPerson.age + 100))
  )
// res17: Either[List[String], ValidatedPerson] = Right(
//   value = ValidatedPerson(
//     name = "ConstValidName",
//     age = 99,
//     socialSecurityNo = "SOCIALNO"
//   )
// )
```

and for `FailFast` transformations:
```scala
given Transformer.Mode.FailFast[[A] =>> Either[String, A]] = 
  Transformer.Mode.FailFast.either[String]

bad
  .into[ValidatedPerson]
  .fallible
  .transform(
    Field.fallibleConst(_.name, ValidatedPerson.Name.make("ConstValidName")),
    Field.fallibleComputed(_.age, unvPerson => ValidatedPerson.Age.make(unvPerson.age + 100))
  )
// res18: Either[String, ValidatedPerson] = Right(
//   value = ValidatedPerson(
//     name = "ConstValidName",
//     age = 99,
//     socialSecurityNo = "SOCIALNO"
//   )
// )
```

##### `Arg` config
All of these work the very same way they do in total transformations:
* `Arg.const`
* `Arg.computed`
* `Arg.renamed`

plus two fallible-specific config options:
* `Arg.fallibleConst`
* `Arg.fallibleComputed`

which work like so for `Accumulating` transformations:
```scala
given Transformer.Mode.Accumulating[[A] =>> Either[List[String], A]] = 
  Transformer.Mode.Accumulating.either[String, List]

bad
  .intoVia(ValidatedPerson.apply)
  .fallible
  .transform(
    Arg.fallibleConst(_.name, ValidatedPerson.Name.makeAccumulating("ConstValidName")),
    Arg.fallibleComputed(_.age, unvPerson => ValidatedPerson.Age.makeAccumulating(unvPerson.age + 100))
  )
// res19: Either[List[String], ValidatedPerson] = Right(
//   value = ValidatedPerson(
//     name = "ConstValidName",
//     age = 99,
//     socialSecurityNo = "SOCIALNO"
//   )
// )
```

and for `FailFast` transformations:
```scala
given Transformer.Mode.FailFast[[A] =>> Either[String, A]] = 
  Transformer.Mode.FailFast.either[String]

bad
  .intoVia(ValidatedPerson.apply)
  .fallible
  .transform(
    Arg.fallibleConst(_.name, ValidatedPerson.Name.make("ConstValidName")),
    Arg.fallibleComputed(_.age, unvPerson => ValidatedPerson.Age.make(unvPerson.age + 100))
  )
// res20: Either[String, ValidatedPerson] = Right(
//   value = ValidatedPerson(
//     name = "ConstValidName",
//     age = 99,
//     socialSecurityNo = "SOCIALNO"
//   )
// )
```

#### Building custom instances of fallible transformers
Life is not always lolipops and crisps and sometimes you need to write a typeclass instance by hand. Worry not though, just like in the case of total transformers, we can easily define custom instances with the help of the configuration DSL (which, let's write it down once again, is a superset of total transformers' DSL).

By all means go wild with the configuration options, I'm too lazy to write them all out here again.
```scala
given Transformer.Mode.Accumulating[[A] =>> Either[List[String], A]] = 
  Transformer.Mode.Accumulating.either[String, List]

val customAccumulating =
  Transformer
    .define[UnvalidatedPerson, ValidatedPerson]
    .fallible
    .build(
      Field.fallibleConst(_.name, ValidatedPerson.Name.makeAccumulating("IAmAlwaysValidNow!"))
    )
// customAccumulating: FallibleTransformer[[A >: Nothing <: Any] => Either[List[String], A], UnvalidatedPerson, ValidatedPerson] = repl.MdocSession$MdocApp10$$anon$45@33d12ba0
```

```scala
given Transformer.Mode.FailFast[[A] =>> Either[String, A]] = 
  Transformer.Mode.FailFast.either[String]

val customFailFast =
  Transformer
    .define[UnvalidatedPerson, ValidatedPerson]
    .fallible
    .build(
      Field.fallibleComputed(_.age, uvp => ValidatedPerson.Age.make(uvp.age + 30))
    )
// customFailFast: FallibleTransformer[[A >: Nothing <: Any] => Either[String, A], UnvalidatedPerson, ValidatedPerson] = repl.MdocSession$MdocApp10$$anon$47@107378dd
```

And for the ones that are not keen on writing out method arguments:
```scala
given Transformer.Mode.Accumulating[[A] =>> Either[List[String], A]] = 
  Transformer.Mode.Accumulating.either[String, List]

val customAccumulatingVia =
  Transformer
    .defineVia[UnvalidatedPerson](ValidatedPerson.apply)
    .fallible
    .build(
      Arg.fallibleConst(_.name, ValidatedPerson.Name.makeAccumulating("IAmAlwaysValidNow!"))
    )
// customAccumulatingVia: FallibleTransformer[[A >: Nothing <: Any] => Either[List[String], A], UnvalidatedPerson, ValidatedPerson] = repl.MdocSession$MdocApp10$$anon$49@527b7c4f
```

```scala
given Transformer.Mode.FailFast[[A] =>> Either[String, A]] = 
  Transformer.Mode.FailFast.either[String]

val customFailFastVia =
  Transformer
    .defineVia[UnvalidatedPerson](ValidatedPerson.apply)
    .fallible
    .build(
      Arg.fallibleComputed(_.age, uvp => ValidatedPerson.Age.make(uvp.age + 30))
    )
// customFailFastVia: FallibleTransformer[[A >: Nothing <: Any] => Either[String, A], UnvalidatedPerson, ValidatedPerson] = repl.MdocSession$MdocApp10$$anon$51@480fac89
```


### A look at the generated code

To inspect the code that is generated you can use `Transformer.Debug.showCode`, this method will print 
the generated code at compile time for you to analyze and see if there's something funny going on after the macro expands.

For the sake of documentation let's also give some examples of what should be the expected output for some basic usages of `ducktape`.

#### Generated code - product transformations
Given a structure of case classes like the ones below let's examine the output that `ducktape` splices into your code:

```scala
import io.github.arainko.ducktape.*

final case class Wrapped[A](value: A) extends AnyVal

case class Person(int: Int, str: Option[String], inside: Inside, collectionOfNumbers: Vector[Float])
case class Person2(int: Wrapped[Int], str: Option[Wrapped[String]], inside: Inside2, collectionOfNumbers: List[Wrapped[Float]])

case class Inside(str: String, int: Int, inside: EvenMoreInside)
case class Inside2(int: Int, str: String, inside: Option[EvenMoreInside2])

case class EvenMoreInside(str: String, int: Int)
case class EvenMoreInside2(str: String, int: Int)

val person = Person(23, Some("str"), Inside("insideStr", 24, EvenMoreInside("evenMoreInsideStr", 25)), Vector.empty)
```
#### Generated code - expansion of `.to`
Calling the `.to` method
```scala
person.to[Person2]
```
expands to:
``` scala 
  to[Person](person)[Person2](
    inline$make$i1[Person, Person2](Transformer.ForProduct)(
      (
        (source: Person) =>
          new Person2(
            int = new Wrapped[Int](source.int),
            str = source.str.map[Wrapped[String]]((src: String) => new Wrapped[String](src)),
            inside = new Inside2(
              int = source.inside.int,
              str = source.inside.str,
              inside =
                Some.apply[EvenMoreInside2](new EvenMoreInside2(str = source.inside.inside.str, int = source.inside.inside.int))
            ),
            collectionOfNumbers = source.collectionOfNumbers
              .map[Wrapped[Float]]((`src₂`: Float) => new Wrapped[Float](`src₂`))
              .to[List[Wrapped[Float]] & Iterable[Wrapped[Float]]](iterableFactory[Wrapped[Float]])
          )
      ): Transformer[Person, Person2]
    ): ForProduct[Person, Person2]
  )
```

#### Generated code - expansion of `.into`
Calling the `.into` method
```scala
person
  .into[Person2]
  .transform(
    Field.const(_.str, Some(Wrapped("ConstString!"))),
    Field.computed(_.int, person => Wrapped(person.int + 100)),
  )
```
expands to:
``` scala 
  {
    val AppliedBuilder_this: AppliedBuilder[Person, Person2] = into[Person](person)[Person2]

    {
      val source$proxy17: Person = AppliedBuilder_this.inline$appliedTo

      {
        val inside$2: Inside2 = new Inside2(
          int = source$proxy17.inside.int,
          str = source$proxy17.inside.str,
          inside = Some.apply[EvenMoreInside2](
            new EvenMoreInside2(str = source$proxy17.inside.inside.str, int = source$proxy17.inside.inside.int)
          )
        )
        val collectionOfNumbers$2: List[Wrapped[Float]] = source$proxy17.collectionOfNumbers
          .map[Wrapped[Float]]((src: Float) => new Wrapped[Float](src))
          .to[List[Wrapped[Float]] & Iterable[Wrapped[Float]]](iterableFactory[Wrapped[Float]])
        val str$2: Some[Wrapped[String]] = Some.apply[Wrapped[String]](Wrapped.apply[String]("ConstString!"))
        val int$2: Wrapped[Int] = Wrapped.apply[Int](source$proxy17.int.+(100))
        new Person2(int = int$2, str = str$2, inside = inside$2, collectionOfNumbers = collectionOfNumbers$2)
      }: Person2
    }: Person2
  }
```

#### Generated code - expansion of `.via`
Calling the `.via` method
```scala
person.via(Person2.apply)
```

expands to:
``` scala 
  {
    val Func$proxy7: FunctionMirror[Function4[Wrapped[Int], Option[Wrapped[String]], Inside2, List[Wrapped[Float]], Person2]] {
      type Return >: Person2 <: Person2
    } = FunctionMirror.asInstanceOf[
      FunctionMirror[Function4[Wrapped[Int], Option[Wrapped[String]], Inside2, List[Wrapped[Float]], Person2]] {
        type Return >: Person2 <: Person2
      }
    ]

    ({
      val int$proxy2: Wrapped[Int] = new Wrapped[Int](person.int)
      val str$proxy2: Option[Wrapped[String]] = person.str.map[Wrapped[String]]((src: String) => new Wrapped[String](src))
      val inside$proxy2: Inside2 = new Inside2(
        int = person.inside.int,
        str = person.inside.str,
        inside = Some.apply[EvenMoreInside2](new EvenMoreInside2(str = person.inside.inside.str, int = person.inside.inside.int))
      )
      val collectionOfNumbers$proxy2: List[Wrapped[Float]] = person.collectionOfNumbers
        .map[Wrapped[Float]]((`src₂`: Float) => new Wrapped[Float](`src₂`))
        .to[List[Wrapped[Float]] & Iterable[Wrapped[Float]]](iterableFactory[Wrapped[Float]])
      Person2.apply(int$proxy2, str$proxy2, inside$proxy2, collectionOfNumbers$proxy2)
    }: Return): Return
  }
```

#### Generated code - expansion of `.intoVia`
Calling the `.intoVia` method with subsequent transformation customizations
```scala
person
  .intoVia(Person2.apply)
  .transform(
    Arg.const(_.str, Some(Wrapped("ConstStr!"))),
    Arg.computed(_.int, person => Wrapped(person.int + 100))
  )
```

expands to:
``` scala 
  {
    val x$4$proxy7: FunctionMirror[Function4[Wrapped[Int], Option[Wrapped[String]], Inside2, List[Wrapped[Float]], Person2]] {
      type Return >: Person2 <: Person2
    } = FunctionMirror.asInstanceOf[
      FunctionMirror[Function4[Wrapped[Int], Option[Wrapped[String]], Inside2, List[Wrapped[Float]], Person2]] {
        type Return >: Person2 <: Person2
      }
    ]
    val builder: AppliedViaBuilder[Person, Return, Function4[Wrapped[Int], Option[Wrapped[String]], Inside2, List[
      Wrapped[Float]
    ], Person2], Nothing] = inline$instance[Person, x$4$proxy7.Return, Function4[Wrapped[Int], Option[
      Wrapped[String]
    ], Inside2, List[Wrapped[Float]], Person2], Nothing](
      person,
      (int: Wrapped[Int], str: Option[Wrapped[String]], inside: Inside2, collectionOfNumbers: List[Wrapped[Float]]) =>
        Person2.apply(int, str, inside, collectionOfNumbers)
    )
    val AppliedViaBuilder_this: AppliedViaBuilder[
      Person,
      Person2,
      Function4[Wrapped[Int], Option[Wrapped[String]], Inside2, List[Wrapped[Float]], Person2],
      FunctionArguments {
        val int: Wrapped[Int]
        val str: Option[Wrapped[String]]
        val inside: Inside2
        val collectionOfNumbers: List[Wrapped[Float]]
      }
    ] =
      builder.asInstanceOf[[ArgSelector >: Nothing <: FunctionArguments] =>> AppliedViaBuilder[Person, Return, Function4[Wrapped[
        Int
      ], Option[Wrapped[String]], Inside2, List[Wrapped[Float]], Person2], ArgSelector][
        FunctionArguments {
          val int: Wrapped[Int]
          val str: Option[Wrapped[String]]
          val inside: Inside2
          val collectionOfNumbers: List[Wrapped[Float]]
        }
      ]]

    {
      val source$proxy19: Person = AppliedViaBuilder_this.inline$source

      AppliedViaBuilder_this.inline$function.apply(
        Wrapped.apply[Int](source$proxy19.int.+(100)),
        Some.apply[Wrapped[String]](Wrapped.apply[String]("ConstStr!")),
        new Inside2(
          int = source$proxy19.inside.int,
          str = source$proxy19.inside.str,
          inside = Some.apply[EvenMoreInside2](
            new EvenMoreInside2(str = source$proxy19.inside.inside.str, int = source$proxy19.inside.inside.int)
          )
        ),
        source$proxy19.collectionOfNumbers
          .map[Wrapped[Float]]((src: Float) => new Wrapped[Float](src))
          .to[List[Wrapped[Float]] & Iterable[Wrapped[Float]]](iterableFactory[Wrapped[Float]])
      ): Person2
    }: Person2
  }
```
