# Ducktape

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.github.arainko/ducktape_3/badge.svg?style=flat-square)](https://maven-badges.herokuapp.com/maven-central/io.github.arainko/ducktape_3)

*Ducktape* is a library for boilerplate-less and configurable transformations between case classes/enums (sealed traits) for Scala 3. Directly inspired by [chimney](https://github.com/scalalandio/chimney).

### Installation
```scala
libraryDependencies += "io.github.arainko" %% "ducktape" % "@VERSION@"
```

### Examples

#### 1. *Case class to case class*

```scala mdoc
import io.github.arainko.ducktape.*

final case class Person(firstName: String, lastName: String, age: Int)
final case class PersonButMoreFields(firstName: String, lastName: String, age: Int, socialSecurityNo: String)

val personWithMoreFields = PersonButMoreFields("John", "Doe", 30, "SOCIAL-NUM-12345")

val transformed = personWithMoreFields.to[Person]

```

Automatic case class to case class transformations are supported given that
the source type has all the fields of a the destination case class and the types corresponding to these fields have an instance of `Transformer` in scope.

If these are not met, a compiletime error is issued:
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
we transform into, otherwise a compiletime errors is issued.

#### 3. *Case class to case class with config*

As we established earlier, going from `Person` to a `PersonButMoreFields` cannot happen automatically as the former
doesn't have the `socialSecurityNo` field, but it has all the other fields so it's almost there, we just have to nudge it a lil' bit.

We can do so with field configurations in 3 ways:
  1. Set a constant to a specific field
  2. Compute the value for a specific field by applying a function
  3. Use a different field in its place - 'rename' it

```scala mdoc:reset
import io.github.arainko.ducktape.*

final case class Person(firstName: String, lastName: String, age: Int)
final case class PersonButMoreFields(firstName: String, lastName: String, age: Int, socialSecurityNo: String)

val person = Person("Jerry", "Smith", 20)

// 1. Set a constant to a specific field
val withConstant = 
  person
    .into[PersonButMoreFields]
    .withFieldConst(_.socialSecurityNo, "CONSTANT-SSN")
    .transform

// 2. Compute the value for a specific field by applying a function
val withComputed = 
  person
    .into[PersonButMoreFields]
    .withFieldComputed(_.socialSecurityNo, p => s"${p.firstName}-COMPUTED-SSN")
    .transform

// 3. Use a different field in its place - 'rename' it
val withRename = 
  person
    .into[PersonButMoreFields]
    .withFieldRenamed(_.socialSecurityNo, _.firstName)
    .transform
```

In case we repeatedly apply configurations to the same field, the latest one is chosen:

```scala mdoc

val withRepeatedConfig =
  person
    .into[PersonButMoreFields]
    .withFieldRenamed(_.socialSecurityNo, _.firstName)
    .withFieldComputed(_.socialSecurityNo, p => s"${p.firstName}-COMPUTED-SSN")
    .withFieldConst(_.socialSecurityNo, "CONSTANT-SSN")
    .transform

```

Of course we can use this to override the automatic derivation per field:

```scala mdoc

val withEverythingOverriden = 
  person
    .into[PersonButMoreFields]
    .withFieldConst(_.socialSecurityNo, "CONSTANT-SSN")
    .withFieldConst(_.age, 100)
    .withFieldConst(_.firstName, "OVERRIDEN-FIRST-NAME")
    .withFieldConst(_.lastName, "OVERRIDEN-LAST-NAME")
    .transform

```

#### 4. Enum to enum with config

Enum transformations, just like case class transformations, can be configured - but only in one way, by applying a function to a specific subtype:

```scala mdoc:reset-object
import io.github.arainko.ducktape.*

enum Size:
  case Small, Medium, Large

enum ExtraSize:
  case ExtraSmall, Small, Medium, Large, ExtraLarge

val transformed = Size.Small.to[ExtraSize]

// Apply a function for the specified subtype
val size = 
  ExtraSize.ExtraSmall
    .into[Size]
    .withCaseInstance[ExtraSize.ExtraSmall.type](_ => Size.Small)
    .withCaseInstance[ExtraSize.ExtraLarge.type](_ => Size.Large)
    .transform
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
a parameter cannot be matched (be it there's no name correspondence or a `Transformer` between types of two fields named the same isn't available):

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

person1
  .intoVia(methodToExpandButOneMoreArg)
  .withArgConst(_.additionalArg, "-CONST ARG")
  .transform
```

We can configure method arguments in 3 ways:
 - `withArgConst` - supply a constant value to a method argument
 - `withArgComputed` - compute the argument with a function
 - `withArgRenamed` - rename an argument so that it matches a different field

### A look at the generated code

#### -- TODO --