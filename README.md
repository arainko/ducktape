# Ducktape

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.github.arainko/ducktape_3/badge.svg?style=flat-square)](https://maven-badges.herokuapp.com/maven-central/io.github.arainko/ducktape_3)

*Ducktape* is a library for boilerplate-less and configurable transformations between case classes/enums (sealed traits) for Scala 3. Directly inspired by [chimney](https://github.com/scalalandio/chimney).

### Installation
```scala
libraryDependencies += "io.github.arainko" %% "ducktape" % "0.0.12"
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
the type you are transforming from has all the fields of a case class you are transforming to and the types corresponding to these fields have an instance of `Transformer` in scope.

If these are not met a compiletime error is issued:
```scala
val person = Person("Jerry", "Smith", 20)

person.to[PersonButMoreFields]

// error:
// No field named 'socialSecurityNo' found in Person
// person.to[PersonButMoreFields]
//                               ^
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

Automatic enum to enum transformations are supported given that the enum we transform contains a subset of cases
we transform into, otherwise a compiletime errors is issued.

