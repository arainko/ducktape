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
```

In case we repeatedly apply configurations to the same field, the latest one is chosen:

```scala mdoc

val withRepeatedConfig =
  person
    .into[PersonButMoreFields]
    .transform(
      Field.renamed(_.socialSecurityNo, _.firstName),
      Field.computed(_.socialSecurityNo, p => s"${p.firstName}-COMPUTED-SSN"),
      Field.const(_.socialSecurityNo, "CONSTANT-SSN")
    )

```

Of course we can use this to override the automatic derivation per field:

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

Enum transformations, just like case class transformations, can be configured - but only in one way, by applying a function to a specific subtype:

```scala
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
// size: ExtraSize = Small
```

### A look at the generated code

#### -- TODO --