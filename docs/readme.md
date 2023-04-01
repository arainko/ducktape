# ducktape

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.github.arainko/ducktape_3/badge.svg?style=flat-square)](https://maven-badges.herokuapp.com/maven-central/io.github.arainko/ducktape_3)

*ducktape* is a library for boilerplate-less and configurable transformations between case classes and enums/sealed traits for Scala 3. Directly inspired by [chimney](https://github.com/scalalandio/chimney).

If this project interests you, please drop a 🌟 - these things are worthless but give me a dopamine rush nonetheless.

### Installation
```scala
libraryDependencies += "io.github.arainko" %% "ducktape" % "@VERSION@"
```
NOTE: the [version scheme](https://www.scala-lang.org/blog/2021/02/16/preventing-version-conflicts-with-versionscheme.html) is set to `early-semver`

### Total transformations - examples

#### 1. *Case class to case class*

```scala mdoc
import io.github.arainko.ducktape.*

final case class Person(firstName: String, lastName: String, age: Int)
final case class PersonButMoreFields(firstName: String, lastName: String, age: Int, socialSecurityNo: String)

val personWithMoreFields = PersonButMoreFields("John", "Doe", 30, "SOCIAL-NUM-12345")

val transformed = personWithMoreFields.to[Person]

```

Automatic case class to case class transformations are supported given that
the source type has all the fields of the destination type and the types corresponding to these fields have an instance of `Transformer` in scope.

If these requirements are not met, a compiletime error is issued:
```scala mdoc:fail
val person = Person("Jerry", "Smith", 20)

person.to[PersonButMoreFields]

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

```scala mdoc:reset
import io.github.arainko.ducktape.*

final case class Person(firstName: String, lastName: String, age: Int)
final case class PersonButMoreFields(firstName: String, lastName: String, age: Int, socialSecurityNo: String = "ssn")

val person = Person("Jerry", "Smith", 20)

// 1. Set a constant to a specific field
val withConstant = 
  person
    .into[PersonButMoreFields]
    .transform(Field.const(_.socialSecurityNo, "CONSTANT-SSN"))

// 2. Compute the value for a specific field by applying a function
val withComputed = 
  person
    .into[PersonButMoreFields]
    .transform(Field.computed(_.socialSecurityNo, p => s"${p.firstName}-COMPUTED-SSN"))

// 3. Use a different field in its place - 'rename' it
val withRename = 
  person
    .into[PersonButMoreFields]
    .transform(Field.renamed(_.socialSecurityNo, _.firstName))

// 4. Use the default value of a specific field (a compiletime error will be issued if the field doesn't have a default)
val withDefault = 
  person
    .into[PersonButMoreFields]
    .transform(Field.default(_.socialSecurityNo))

final case class FieldSource(lastName: String, socialSecurityNo: String)

// 5. Grab and use all matching fields from a different case class (a compiletime error will be issued if none of the fields match)
val withAllMatchingFields = 
  person
    .into[PersonButMoreFields]
    .transform(Field.allMatching(FieldSource("SourcedLastName", "SOURCED-SSN")))
```

In case we repeatedly apply configurations to the same field a warning is emitted (which can be ignored with `@nowarn`) and the latest one is chosen:

```scala mdoc:warn

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
```

Of course we can use this to override the automatic derivation for each field:

```scala mdoc

val withEverythingOverriden = 
  person
    .into[PersonButMoreFields]
    .transform(
      Field.const(_.socialSecurityNo, "CONSTANT-SSN"),
      Field.const(_.age, 100),
      Field.const(_.firstName, "OVERRIDEN-FIRST-NAME"),
      Field.const(_.lastName, "OVERRIDEN-LAST-NAME"),
    )

```

#### 4. Enum to enum with config

Enum transformations, just like case class transformations, can be configured by:
* supplying a constant value with `Case.const`,
* supplying a function that will be applied to the chosen subtype with `Case.computed`.

```scala mdoc:reset-object
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

// Specify a function to transform a given case with that function
val withComputed =
  ExtraSize.ExtraSmall
    .into[Size]
    .transform(
      Case.computed[ExtraSize.ExtraSmall.type](_ => Size.Small),
      Case.computed[ExtraSize.ExtraLarge.type](_ => Size.Large)
    )
    
```

#### 5. Method to case class

We can also let `ducktape` expand method incovations for us:

```scala mdoc:reset
import io.github.arainko.ducktape.*

final case class Person1(firstName: String, lastName: String, age: Int)
final case class Person2(firstName: String, lastName: String, age: Int)

def methodToExpand(lastName: String, age: Int, firstName: String): Person2 =
  Person2(firstName, lastName, age)

val person1: Person1 = Person1("John", "Doe", 23)
val person2: Person2 = person1.via(methodToExpand)
```

In this case, `ducktape` will match the fields from `Person` to parameter names of `methodToExpand` failing at compiletime if
a parameter cannot be matched (be it there's no name correspondence or a `Transformer` between types of two fields with the same name isn't available):

```scala mdoc:fail:silent
def methodToExpandButOneMoreArg(lastName: String, age: Int, firstName: String, additionalArg: String): Person2 =
  Person2(firstName + additionalArg, lastName, age)

person1.via(methodToExpandButOneMoreArg)
// error:
// No field named 'additionalArg' in Person
```

#### 6. Method to case class with config

Just like transforming between case classes and coproducts we can nudge the derivation in some places to complete the puzzle, let's
tackle the last example once again:

```scala mdoc
def methodToExpandButOneMoreArg(lastName: String, age: Int, firstName: String, additionalArg: String): Person2 =
  Person2(firstName + additionalArg, lastName, age)

val withConstant = 
  person1
    .intoVia(methodToExpandButOneMoreArg)
    .transform(Arg.const(_.additionalArg, "-CONST ARG"))

val withComputed = 
  person1
    .intoVia(methodToExpandButOneMoreArg)
    .transform(Arg.computed(_.additionalArg, _.lastName + "-COMPUTED"))

val withRenamed = 
  person1
    .intoVia(methodToExpandButOneMoreArg)
    .transform(Arg.renamed(_.additionalArg, _.lastName))
```

#### 7. Automatic wrapping and unwrapping of `AnyVal`

Despite being a really flawed abstraction `AnyVal` is pretty prevalent in Scala 2 code that you may want to interop with
and `ducktape` is here to assist you. `Transformer` definitions for wrapping and uwrapping `AnyVals` are
automatically available:

```scala mdoc:reset-object
import io.github.arainko.ducktape.*

final case class WrappedString(value: String) extends AnyVal

val wrapped = WrappedString("I am a String")

val unwrapped = wrapped.to[String]

val wrappedAgain = unwrapped.to[WrappedString]
```

#### 8. Defining custom `Transformers`

If for some reason you need a custom `Transformer` in scope but still want to partially rely
on the automatic derivation and have all the configuration DSL goodies you can use these:

* `Transformer.define[Source, Dest].build(<Field/Case configuration>)`
* `Transformer.defineVia[Source](someMethod).build(<Arg configuration>)`
  
Examples:

```scala mdoc:reset
import io.github.arainko.ducktape.*

final case class TestClass(str: String, int: Int)
final case class TestClassWithAdditionalList(int: Int, str: String, additionalArg: List[String])

def method(str: String, int: Int, additionalArg: List[String]) = TestClassWithAdditionalList(int, str, additionalArg)

val testClass = TestClass("str", 1)

val definedViaTransformer =
  Transformer
    .defineVia[TestClass](method)
    .build(Arg.const(_.additionalArg, List("const")))

val definedTransformer =
  Transformer
    .define[TestClass, TestClassWithAdditionalList]   
    .build(Field.const(_.additionalArg, List("const")))

val transformedVia = definedViaTransformer.transform(testClass)

val transformed = definedTransformer.transform(testClass)
```

#### Usecase: recursive `Transformers`

Recursive instances are lazy by nature so automatic derivation will be of no use here, we need to get our hands a little bit dirty:

```scala mdoc:reset
import io.github.arainko.ducktape.*

final case class Rec[A](value: A, rec: Option[Rec[A]])

given recursive[A, B](using Transformer[A, B]): Transformer[Rec[A], Rec[B]] = 
  Transformer.define[Rec[A], Rec[B]].build()

Rec("1", Some(Rec("2", Some(Rec("3", None))))).to[Rec[Option[String]]]
```

### Fallible transfomations - examples
Sometimes ordinary field mappings just do not cut it, more often than not our domain model's constructors are hidden behind a safe factory method, eg.:

```scala mdoc:reset
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

```scala mdoc

final case class UnvalidatedPerson(name: String, age: Int)

val unvalidatedPerson = UnvalidatedPerson("ValidName", -1)

val transformed = unvalidatedPerson.via(ValidatedPerson.create)
```

But this quickly falls apart when nested transformations are introduced and we're pretty much back to square one where we're on our own to write the boilerplate.

That's where `Fallible Transformers` and their flavors come in: 
* `Accumulating` for error accumulation,
* `FailFast` for the cases where we just want to bail at the very first sight of trouble.

Let's look at the definition of both of these, starting with `Accumulating`.

#### Definition of `Transformer.Accumulating`

```scala
trait Accumulating[F[+x], Source, Dest] {
  def transform(value: Source): F[Dest]
}

object Accumulating {
  trait Support[F[+x]] {
    def pure[A](value: A): F[A]
    def map[A, B](fa: F[A], f: A => B): F[B]
    def product[A, B](fa: F[A], fb: F[B]): F[(A, B)]
  }
}
```

So an `Accumulating` transformer takes a `Source` and gives back a `Dest` wrapped in an `F` where `F` is the wrapper type for our transformations eg. if `F[+x]` = `Either[List[String], x]` then the `transform` method will return an `Either[List[String], Dest]` (the `List` on the left side will accumulate errors when multiple fallible transformations are involved).

Moving on to `Accumulating.Support`, what exactly is it and why do we need it? So the `Accumulating.Support` typeclass gives some meaning to an abstract wrapper type `F` so that we can actually operate on it in some way, let's go through all the methods that `Accumulating.Support` exposes:
* `pure` is for wrapping arbitrary values into `F`, eg. if `F[+x] = Either[List[String], x]` then calling `pure` would involve just wrapping the value in a `Right.apply` call.

* `map` is for operating on the wrapped values, eg. if we find ourselves with a `F[Int]` in hand and we want to transform the value 'inside' to a `String` we can call `.map(_.toString)` to yield a `F[String]`

* `product` enables composition of two unrelated values wrapped in an `F` which is THE operator for accumulating errors.

`ducktape` provides instances for `Either` with any subtype of `Iterable` on the left side, so that eg. `Transformer.Accumulating.Support[[A] =>> Either[List[String], A]]` is available out of the box.

#### Definition of `Transformer.FailFast`

```scala
trait FailFast[F[+x], Source, Dest] {
  def transform(value: Source): F[Dest]
}

object FailFast {
  trait Support[F[+x]] {
    def pure[A](value: A): F[A]
    def map[A, B](fa: F[A], f: A => B): F[B]
    def flatMap[A, B](fa: F[A], f: A => F[B]): F[B]
  }
}
```

The definition itself is pretty much the same as in `Accumulating` with one key change, `FailFast.Support` exchanges the `product` method for `flatMap` which implies short-circuit semantics for the composition operator.

`ducktape` provides instances for `Transformer.FailFast.Support[Option]` and for `Either` no matter what is on the left side (eg. `Transformer.FailFast.Support[[A] =>> Either[String, A]]`) out of the box.

#### Automatic fallible transformations

Now for the meat and potatoes of `Fallible Transformers`. To make use of the derivation mechanism that `ducktape` provides we should strive for our model to be modeled in a specific way - with a new nominal type per each validated field, which comes down to... Newtypes! (eg. [monix-newtypes](https://github.com/monix/newtypes))

Let's define a minimalist newtype abstraction that will also do validation (this is a one-time effort that can easily be extracted to a library):

```scala mdoc
type FailFastValidation[+A] = Either[String, A]
type AccValidation[+A] = Either[List[String], A]

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
  given accumulatingWrappingTransformer: Transformer.Accumulating[[a] =>> Either[List[String], a], A, Type] = makeAccumulating(_)

  given failFastWrappingTransformer: Transformer.FailFast[[a] =>> Either[String, a], A, Type] = make(_)

  given unwrappingTransformer: Transformer[Type, A] = _.value

}
```

Now let's get back to the definition of `ValidatedPerson` and tweak it a little:

```scala mdoc:nest
final case class ValidatedPerson(name: ValidatedPerson.Name, age: ValidatedPerson.Age)

object ValidatedPerson {
  object Name extends NewtypeValidated[String](str => !str.isBlank, "Name should not be blank!")
  export Name.Type as Name

  object Age extends NewtypeValidated[Int](int => int > 0, "Age should be positive!")
  export Age.Type as Age
}
```

We introduce a newtype for each field, this way we can keep our invariants at compiletime and also let `ducktape` do it's thing:

```scala mdoc

val bad = UnvalidatedPerson(name = "", age = -1)
val good = UnvalidatedPerson(name = "ValidName", age = 24)


bad.accumulatingTo[[A] =>> Either[List[String], A], ValidatedPerson]
good.accumulatingTo[[A] =>> Either[List[String], A], ValidatedPerson]

bad.failFastTo[[A] =>> Either[String, A], ValidatedPerson]
good.failFastTo[[A] =>> Either[String, A], ValidatedPerson]
```

### A look at the generated code

To inspect the code that is generated you can use `Transformer.Debug.showCode`, this method will print 
the generated code at compile time for you to analyze and see if there's something funny going on after the macro expands.

For the sake of documentation let's also give some examples of what should be the expected output for some basic usages of `ducktape`.

#### Generated code - product transformations
Given a structure of case classes like the ones below let's examine the output that `ducktape` splices into your code:

```scala mdoc:reset-object:silent
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
```scala mdoc:silent
person.to[Person2]
```
expands to:
```scala mdoc:passthrough
import io.github.arainko.ducktape.docs.*

Docs.printCode(person.to[Person2])
```

#### Generated code - expansion of `.into`
Calling the `.into` method
```scala mdoc:silent
person
  .into[Person2]
  .transform(
    Field.const(_.str, Some(Wrapped("ConstString!"))),
    Field.computed(_.int, person => Wrapped(person.int + 100)),
  )
```
expands to:
```scala mdoc:passthrough
import io.github.arainko.ducktape.docs.*

Docs.printCode(
  person
    .into[Person2]
    .transform(
      Field.const(_.str, Some(Wrapped("ConstString!"))),
      Field.computed(_.int, person => Wrapped(person.int + 100)),
    )
)
```

#### Generated code - expansion of `.via`
Calling the `.via` method
```scala mdoc:silent
person.via(Person2.apply)
```

expands to:
```scala mdoc:passthrough
import io.github.arainko.ducktape.docs.*

Docs.printCode(person.via(Person2.apply))
```

#### Generated code - expansion of `.intoVia`
Calling the `.intoVia` method with subsequent transformation customizations
```scala mdoc:silent
person
  .intoVia(Person2.apply)
  .transform(
    Arg.const(_.str, Some(Wrapped("ConstStr!"))),
    Arg.computed(_.int, person => Wrapped(person.int + 100))
  )
```

expands to:
```scala mdoc:passthrough
import io.github.arainko.ducktape.docs.*

Docs.printCode(
  person
  .intoVia(Person2.apply)
  .transform(
    Arg.const(_.str, Some(Wrapped("ConstStr!"))),
    Arg.computed(_.int, person => Wrapped(person.int + 100))
  )
)

``` 
