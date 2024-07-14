# Quick start

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.github.arainko/ducktape_3/badge.svg?style=flat-square)](https://maven-badges.herokuapp.com/maven-central/io.github.arainko/ducktape_3)

*ducktape* is a library for boilerplate-less and configurable transformations between case classes and enums/sealed traits for Scala 3. Directly inspired by [chimney](https://github.com/scalalandio/chimney).

If this project interests you, please [drop a 🌟](https://github.com/arainko/ducktape) - these things are worthless but give me a dopamine rush nonetheless.

## Installation
```scala
libraryDependencies += "io.github.arainko" %% "ducktape" % "@VERSION@"

// or if you're using Scala.js or Scala Native
libraryDependencies += "io.github.arainko" %%% "ducktape" % "@VERSION@"
```

NOTE: the [version scheme](https://www.scala-lang.org/blog/2021/02/16/preventing-version-conflicts-with-versionscheme.html) is set to `early-semver`

You're currently browsing the documentation for `ducktape 0.2.x`, if you're looking for the `0.1.x` docs go here: https://github.com/arainko/ducktape/tree/series/0.1.x#-ducktape

## Entrypoint of the library

The user-facing API of `ducktape` is mostly a bunch of extension methods that allow us to transform between types in a variety of ways, the only import needed to get started looks like this:

```scala
import io.github.arainko.ducktape.*
```

## Motivating example

`ducktape` is all about painlessly transforming between similiarly structured case classes/enums/sealed traits. If we were to define two really, really similar sets of case class and/or enums, eg. ones like these:

@:select(model)
@:choice(wire)
```scala mdoc
import java.time.Instant
import io.github.arainko.ducktape.*

object wire:
  final case class Person(
    firstName: String,
    lastName: String,
    paymentMethods: List[wire.PaymentMethod],
    status: wire.Status,
    updatedAt: Option[Instant]
  )

  enum Status:
    case Registered, PendingRegistration, Removed

  enum PaymentMethod:
    case Card(name: String, digits: Long, expires: Instant)
    case PayPal(email: String)
    case Cash
```

@:choice(domain)
```scala mdoc
import java.time.Instant
import io.github.arainko.ducktape.*

object domain:
  final case class Person( // <-- fields reshuffled
    lastName: String,
    firstName: String,
    status: Option[domain.Status], // <-- 'status' in the domain model is optional
    paymentMethods: Vector[domain.Payment], // <-- collection type changed from a List to a Vector
    updatedAt: Option[Instant]
  )

  enum Status:
    case Registered, PendingRegistration, Removed
    case PendingRemoval // <-- additional enum case

  enum Payment:
    case Card(name: String, digits: Long, expires: Instant)
    case PayPal(email: String)
    case Cash
```
@:@

...and an input instance that we intend to transform into its `domain` counterpart:
```scala mdoc:silent
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

...then transforming between the `wire` and `domain` models is just a matter of calling `.to[domain.Person]` on the input:

@:select(underlying-code-1)
@:choice(visible)
```scala mdoc
wirePerson.to[domain.Person]
```

@:choice(generated)
```scala mdoc:passthrough
import io.github.arainko.ducktape.docs.*

Docs.printCode(
  wirePerson.to[domain.Person]
)
``` 
@:@

But now imagine that your wire model differs ever so slightly from your domain model, maybe the wire model's `PaymentMethod.Card` doesn't have the `name` field for some inexplicable reason...

@:select(model)
@:choice(wire)
```scala mdoc:reset
import java.time.Instant
import io.github.arainko.ducktape.*

object wire:
  final case class Person(
    firstName: String,
    lastName: String,
    paymentMethods: List[wire.PaymentMethod],
    status: wire.Status,
    updatedAt: Option[Instant]
  )

  enum Status:
    case Registered, PendingRegistration, Removed

  enum PaymentMethod:
    case Card(digits: Long, expires: Instant) // <-- poof, 'name' is gone
    case PayPal(email: String)
    case Cash
```

@:choice(domain)
```scala mdoc
import java.time.Instant
import io.github.arainko.ducktape.*

object domain:
  final case class Person(
    lastName: String,
    firstName: String,
    status: Option[domain.Status],
    paymentMethods: Vector[domain.Payment],
    updatedAt: Option[Instant]
  )

  enum Status:
    case Registered, PendingRegistration, Removed
    case PendingRemoval

  enum Payment:
    case Card(name: String, digits: Long, expires: Instant)
    case PayPal(email: String)
    case Cash
```
@:@

```scala mdoc:invisible
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

@:style(long-quote)
`No field 'name' found in MdocApp0.this.wire.PaymentMethod.Card @ Person.paymentMethods.element.at[MdocApp0.this.domain.Payment.Card].name`
@:@

especially the part after `@`:

@:style(long-quote)
`Person.paymentMethods.element.at[MdocApp0.this.domain.Payment.Card].name`
@:@

the thing above is basically a path to the field/subtype under which `ducktape` was not able to create a transformation, these are meant to be copy-pastable for when you're actually trying to fix the error, eg. by setting the `name` field to a constant value:

@:select(underlying-code-2)

@:choice(visible)
```scala mdoc
val domainPerson =
  wirePerson
    .into[domain.Person]
    .transform(Field.const(_.paymentMethods.element.at[domain.Payment.Card].name, "CONST NAME"))
```

@:choice(generated)
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
@:@

Read more in the chapter dedicated to [configuring transformations](total_transformations/configuring_transformations.md).

To get an idea of what transformations are actually supported head on over to [transformation rules](../transformation_rules.md).
