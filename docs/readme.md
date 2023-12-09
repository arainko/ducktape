# ![ducktape-logo-32](https://user-images.githubusercontent.com/46346508/236060869-3b118075-f660-44c9-9d0d-d40fba5c8db0.svg) ducktape 0.2.x

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.github.arainko/ducktape_3/badge.svg?style=flat-square)](https://maven-badges.herokuapp.com/maven-central/io.github.arainko/ducktape_3)

*ducktape* is a library for boilerplate-less and configurable transformations between case classes and enums/sealed traits for Scala 3. Directly inspired by [chimney](https://github.com/scalalandio/chimney).

If this project interests you, please drop a 🌟 - these things are worthless but give me a dopamine rush nonetheless.

## Installation
```scala
libraryDependencies += "io.github.arainko" %% "ducktape" % "@VERSION@"

// or if you're using Scala.js or Scala Native
libraryDependencies += "io.github.arainko" %%% "ducktape" % "@VERSION@"
```

NOTE: the [version scheme](https://www.scala-lang.org/blog/2021/02/16/preventing-version-conflicts-with-versionscheme.html) is set to `early-semver`

You're currently browsing the documentation for `ducktape 0.2.x`, if you're looking for the `0.1.x` docs go here: https://github.com/arainko/ducktape/tree/series/0.1.x#-ducktape

## Motivating example

`ducktape` is all about painlessly transforming between similiarly structured case classes/enums/sealed traits:

```scala mdoc:silent
import java.time.Instant
import io.github.arainko.ducktape.*

// imagine this is a wire model of some kind - JSON, protobuf, avro, what have you...
object wire {
  final case class Person(
    firstName: String,
    lastName: String,
    paymentMethods: List[wire.PaymentMethod],
    status: wire.Status,
    updatedAt: Option[Instant],
  )

  enum Status:
    case Registered, PendingRegistration, Removed

  enum PaymentMethod:
    case Card(name: String, digits: Long, expires: Instant)
    case PayPal(email: String)
    case Cash
}

object domain {
  final case class Person( // <-- fields reshuffled 
    lastName: String,
    firstName: String,
    status: Option[domain.Status], // <-- 'status' in the domain model is optional
    paymentMethods: Vector[domain.Payment], // <-- collection type changed from a List to a Vector
    updatedAt: Option[Instant],
  )

  enum Status:
    case Registered, PendingRegistration, Removed
    case PendingRemoval // <-- additional enum case

  enum Payment:
    case Card(name: String, digits: Long, expires: Instant)
    case PayPal(email: String)
    case Cash
}

val wirePerson: wire.Person = wire.Person(
  "John",
  "Doe",
  List(
    wire.PaymentMethod.Cash,
    wire.PaymentMethod.PayPal("john@doe.com"),
    wire.PaymentMethod.Card("J. Doe", 12345, Instant.now)
  ),
  wire.Status.PendingRegistration,
  Some(Instant.ofEpochSecond(0))
)
```

```scala mdoc
val domainPerson = wirePerson.to[domain.Person]
```

<details>
  <summary>Click to see the generated code</summary>
  
```scala mdoc:passthrough
  import io.github.arainko.ducktape.docs.*

  Docs.printCode(
    wirePerson.to[domain.Person]
  )
``` 
</details>


But now imagine that your wire model differs ever so slightly from your domain model, maybe the wire model's `PaymentMethod.Card` doesn't have the `name` field for some inexplicable reason...

```scala mdoc:reset:invisible
import java.time.Instant
import io.github.arainko.ducktape.*

object domain {
  final case class Person( 
    lastName: String,
    firstName: String,
    status: Option[domain.Status],
    paymentMethods: Vector[domain.Payment],
    updatedAt: Option[Instant],
  )

  enum Status:
    case Registered, PendingRegistration, Removed
    case PendingRemoval

  enum Payment:
    case Card(name: String, digits: Long, expires: Instant)
    case PayPal(email: String)
    case Cash
}
```

```scala mdoc:silent
object wire {
  final case class Person(
    firstName: String,
    lastName: String,
    paymentMethods: List[wire.PaymentMethod],
    status: wire.Status,
    updatedAt: Option[Instant],
  )

  enum Status:
    case Registered, PendingRegistration, Removed

  enum PaymentMethod:
    case Card(digits: Long, expires: Instant) // <-- poof, 'name' is gone
    case PayPal(email: String)
    case Cash
}

val wirePerson: wire.Person = wire.Person(
  "John",
  "Doe",
  List(
    wire.PaymentMethod.Cash,
    wire.PaymentMethod.PayPal("john@doe.com"),
    wire.PaymentMethod.Card(12345, Instant.now)
  ),
  wire.Status.PendingRegistration,
  Some(Instant.ofEpochSecond(0))
)
```
...and when you try to transform between these two representations the compiler now yells at you.
```scala mdoc:fail
val domainPerson = wirePerson.to[domain.Person]
```

Now onto dealing with that, let's first examine the error message:

`No field 'name' found in MdocApp0.this.wire.PaymentMethod.Card @ Person.paymentMethods.element.at[MdocApp0.this.domain.Payment.Card].name`

especially the part after `@`:

`Person.paymentMethods.element.at[MdocApp0.this.domain.Payment.Card].name`

the thing above is basically a path to the field/subtype under which `ducktape` was not able to create a transformation, these are meant to be copy-pastable for when you're actually trying to fix the error, eg. by setting the `name` field to a constant value:

```scala mdoc
val domainPerson = 
  wirePerson
    .into[domain.Person]
    .transform(Field.const(_.paymentMethods.element.at[domain.Payment.Card].name, "CONST NAME"))
```

<details>
  <summary>Click to see the generated code</summary>
  
```scala mdoc:passthrough
  import io.github.arainko.ducktape.docs.*

  val wirePerson1: wire.Person = wire.Person(
    "John",
    "Doe",
    List(
      wire.PaymentMethod.Cash,
      wire.PaymentMethod.PayPal("john@doe.com"),
      wire.PaymentMethod.Card(12345, Instant.now)
    ),
    wire.Status.PendingRegistration,
    Some(Instant.ofEpochSecond(0))
  )

  Docs.printCode(
    wirePerson1
      .into[domain.Person]
      .transform(Field.const(_.paymentMethods.element.at[domain.Payment.Card].name, "CONST NAME"))
  )
``` 
</details>

## Transfomation rules

Let's go over the priority and rules that `ducktape` uses to create a transformation (in the same order they're tried in the implementation):

### 1. User supplied `Transformers`

Custom instances of a `Transfomer` are always prioritized since these also function as an extension mechanism of the library.

```scala mdoc
// this transformation is not supported out of the box
given Transformer[String, List[String]] = str => str :: Nil

"single value".to[List[String]]
```

<details>
  <summary>Click to see the generated code</summary>

```scala mdoc:passthrough
  Docs.printCode("single value".to[List[String]])
``` 
</details>

### 2. Upcasting

Transforming a type to its supertype is just an upcast.

```scala mdoc
// (Int | String) >: Int
1.to[Int | String]
```
<details>
  <summary>Click to see the generated code</summary>

```scala mdoc:passthrough
  Docs.printCode(1.to[Int | String])
``` 
</details>

### 3. Mapping over an `Option`

Transforming between options comes down to mapping over it and recursively deriving a transformation for the value inside.

```scala mdoc
given Transformer[Int, String] = int => int.toString

Option(1).to[Option[String]]
```

<details>
  <summary>Click to see the generated code</summary>

```scala mdoc:passthrough
  Docs.printCode(Option(1).to[Option[String]])
``` 
</details>

### 4. Transforming and wrapping in an `Option`

If a transformation between two types is possible then transforming between the source type and an `Option` of the destination type is just wrapping the transformation result in a `Some`.

```scala mdoc
1.to[Option[Int | String]]
```

<details>
  <summary>Click to see the generated code</summary>

```scala mdoc:passthrough
  Docs.printCode(1.to[Option[Int | String]])
``` 
</details>

### 5. Mapping over and changing the collection type

```scala mdoc:nest
//`.to` is already a method on collections
import io.github.arainko.ducktape.to as convertTo

List(1, 2, 3, 4).convertTo[Vector[Int | String]]
```

<details>
  <summary>Click to see the generated code</summary>

```scala mdoc:passthrough
  Docs.printCode(List(1, 2, 3, 4).convertTo[Vector[Int | String]])
``` 
</details>

### 6. Transforming between case classes

```scala mdoc:reset
import io.github.arainko.ducktape.*

case class SourceToplevel(level1: SourceLevel1)
case class SourceLevel1(extra: String, int: Int, level2s: List[SourceLevel2])
case class SourceLevel2(value: Int)

case class DestToplevel(level1: DestLevel1)
case class DestLevel1(int: Int | String, level2s: Vector[DestLevel2])
case class DestLevel2(value: Option[Int])

SourceToplevel(SourceLevel1("extra", 1, List(SourceLevel2(1), SourceLevel2(2)))).to[DestToplevel]
```
<details>
  <summary>Click to see the generated code</summary>

```scala mdoc:passthrough
  import io.github.arainko.ducktape.docs.*

  Docs.printCode(
    SourceToplevel(SourceLevel1("extra", 1, List(SourceLevel2(1), SourceLevel2(2)))).to[DestToplevel]
    )
``` 
</details>


### 7. Transforming between enums/sealed traits

### 8. Same named singletons

### 9. Unwrapping a value class

### 10. Wrapping a value class

### 11. Automatically derived `Transformer.Derived`

## Cookbook (TODO: replace examples with this)

### Case class to case class

## Paths, how do they work? (TODO)

## Coming from ducktape 0.1.x (TODO)

## Popping the hood (TODO)

### Total transformations - examples

#### 1. *Case class to case class*

```scala mdoc
import io.github.arainko.ducktape.*

final case class Person(firstName: String, lastName: String, age: Int)
final case class PersonButMoreFields(firstName: String, lastName: String, age: Int, socialSecurityNo: String, extra: String)

val personWithMoreFields = PersonButMoreFields("John", "Doe", 30, "SOCIAL-NUM-12345", "extra")

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

```scala mdoc:reset
import io.github.arainko.ducktape.*

enum Size:
  case Small, Medium, Large

enum ExtraSize:
  case ExtraSmall, Small, Medium, Large, ExtraLarge

val transformed = Size.Small.to[ExtraSize]
```

We can't go to a coproduct that doesn't contain all of our cases (name wise):

```scala mdoc:fail
val size = ExtraSize.Small.to[Size]
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

```scala mdoc

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

given recursive[A, B](using Transformer.Derived[A, B]): Transformer[Rec[A], Rec[B]] = 
  Transformer.define[Rec[A], Rec[B]].build()

Rec("1", Some(Rec("2", Some(Rec("3", None))))).to[Rec[Option[String]]]
```

### A look at the generated code (TODO: replace this and use expandable details tags to show the generated code)

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
