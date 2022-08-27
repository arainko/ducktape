# Ducktape

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.github.arainko/ducktape_3/badge.svg?style=flat-square)](https://maven-badges.herokuapp.com/maven-central/io.github.arainko/ducktape_3)

*Ducktape* is a library for boilerplate-less and configurable transformations between case classes/enums (sealed traits) for Scala 3. Directly inspired by [chimney](https://github.com/scalalandio/chimney).

If this project interests you, please drop a 🌟 - these things are worthless but give me a dopamine rush nonetheless.

### Installation
```scala
libraryDependencies += "io.github.arainko" %% "ducktape" % "0.1.0-RC1"
```

### Examples

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
//     .define[TestClass, TestClassWithAdditionalList]   
//
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
doesn't have the `socialSecurityNo` field, but it has all the other fields so it's almost there, we just have to nudge it a lil' bit.

We can do so with field configurations in 3 ways:
  1. Set a constant to a specific field with `Field.const`
  2. Compute the value for a specific field by applying a function with `Field.computed`
  3. Use a different field in its place - 'rename' it with `Field.renamed`

```scala
import io.github.arainko.ducktape.*

final case class Person(firstName: String, lastName: String, age: Int)
final case class PersonButMoreFields(firstName: String, lastName: String, age: Int, socialSecurityNo: String)

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
```

In case we repeatedly apply configurations to the same field, the latest one is chosen:

```scala
val withRepeatedConfig =
  person
    .into[PersonButMoreFields]
    .transform(
      Field.renamed(_.socialSecurityNo, _.firstName),
      Field.computed(_.socialSecurityNo, p => s"${p.firstName}-COMPUTED-SSN"),
      Field.const(_.socialSecurityNo, "CONSTANT-SSN")
    )
// withRepeatedConfig: PersonButMoreFields = PersonButMoreFields(
//   firstName = "Jerry",
//   lastName = "Smith",
//   age = 20,
//   socialSecurityNo = "CONSTANT-SSN"
// )
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
a parameter cannot be matched (be it there's no name correspondence or a `Transformer` between types of two fields named the same isn't available):

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
and `ducktape` is here to assist you. `Transformer` definitions for wrapping and uwrapping `AnyVals`
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
// definedViaTransformer: Transformer[TestClass, TestClassWithAdditionalList] = repl.MdocSession$MdocApp6$$Lambda$8962/0x0000000802e7e040@61870c88

val definedTransformer =
  Transformer
    .define[TestClass, TestClassWithAdditionalList]   
    .build(Field.const(_.additionalArg, List("const")))
// definedTransformer: Transformer[TestClass, TestClassWithAdditionalList] = repl.MdocSession$MdocApp6$$Lambda$8963/0x0000000802e7e440@2f13a6ac

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


### A look at the generated code

#### -- TODO --