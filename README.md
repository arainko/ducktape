# ![ducktape-logo-32](https://user-images.githubusercontent.com/46346508/236060869-3b118075-f660-44c9-9d0d-d40fba5c8db0.svg) ducktape 0.2.x

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.github.arainko/ducktape_3/badge.svg?style=flat-square)](https://maven-badges.herokuapp.com/maven-central/io.github.arainko/ducktape_3)

*ducktape* is a library for boilerplate-less and configurable transformations between case classes and enums/sealed traits for Scala 3. Directly inspired by [chimney](https://github.com/scalalandio/chimney).

If this project interests you, please drop a 🌟 - these things are worthless but give me a dopamine rush nonetheless.

## Installation
```scala
libraryDependencies += "io.github.arainko" %% "ducktape" % "0.2.0-M5"

// or if you're using Scala.js or Scala Native
libraryDependencies += "io.github.arainko" %%% "ducktape" % "0.2.0-M5"
```

NOTE: the [version scheme](https://www.scala-lang.org/blog/2021/02/16/preventing-version-conflicts-with-versionscheme.html) is set to `early-semver`

You're currently browsing the documentation for `ducktape 0.2.x`, if you're looking for the `0.1.x` docs go here: https://github.com/arainko/ducktape/tree/series/0.1.x#-ducktape

## Documentation

Head on over to the [docs site](https://arainko.github.io/ducktape/)!

## Motivating example

`ducktape` is all about painlessly transforming between similiarly structured case classes/enums/sealed traits:

```scala
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

```scala
val domainPerson = wirePerson.to[domain.Person]
// domainPerson: Person = Person(
//   lastName = "Doe",
//   firstName = "John",
//   status = Some(value = PendingRegistration),
//   paymentMethods = Vector(
//     Cash,
//     PayPal(email = "john@doe.com"),
//     Card(
//       name = "J. Doe",
//       digits = 12345L,
//       expires = 2024-03-10T00:21:33.860394305Z
//     )
//   ),
//   updatedAt = Some(value = 1970-01-01T00:00:00Z)
// )
```

<details>
  <summary>Click to see the generated code</summary>
  
``` scala 
  (({
    val paymentMethods$2: Vector[Payment] = MdocApp.this.wirePerson.paymentMethods
      .map[Payment]((src: PaymentMethod) =>
        if (src.isInstanceOf[Card])
          new Card(
            name = src.asInstanceOf[Card].name,
            digits = src.asInstanceOf[Card].digits,
            expires = src.asInstanceOf[Card].expires
          )
        else if (src.isInstanceOf[PayPal]) new PayPal(email = src.asInstanceOf[PayPal].email)
        else if (src.isInstanceOf[Cash.type]) MdocApp.this.domain.Payment.Cash
        else throw new RuntimeException("Unhandled case. This is most likely a bug in ducktape.")
      )
      .to[Vector[Payment]](iterableFactory[Payment])
    val status$2: Some[Status] = Some.apply[Status](
      if (MdocApp.this.wirePerson.status.isInstanceOf[Registered.type]) MdocApp.this.domain.Status.Registered
      else if (MdocApp.this.wirePerson.status.isInstanceOf[PendingRegistration.type])
        MdocApp.this.domain.Status.PendingRegistration
      else if (MdocApp.this.wirePerson.status.isInstanceOf[Removed.type]) MdocApp.this.domain.Status.Removed
      else throw new RuntimeException("Unhandled case. This is most likely a bug in ducktape.")
    )
    new Person(
      lastName = MdocApp.this.wirePerson.lastName,
      firstName = MdocApp.this.wirePerson.firstName,
      status = status$2,
      paymentMethods = paymentMethods$2,
      updatedAt = MdocApp.this.wirePerson.updatedAt
    )
  }: Person): Person)
```
</details>


But now imagine that your wire model differs ever so slightly from your domain model, maybe the wire model's `PaymentMethod.Card` doesn't have the `name` field for some inexplicable reason...


```scala
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
```scala
val domainPerson = wirePerson.to[domain.Person]
// error:
// No field 'name' found in MdocApp0.this.wire.PaymentMethod.Card @ Person.paymentMethods.element.at[MdocApp0.this.domain.Payment.Card].name
// given Transformer[Int, String] = int => int.toString
//                                 ^
```

Now onto dealing with that, let's first examine the error message:

`No field 'name' found in MdocApp0.this.wire.PaymentMethod.Card @ Person.paymentMethods.element.at[MdocApp0.this.domain.Payment.Card].name`

especially the part after `@`:

`Person.paymentMethods.element.at[MdocApp0.this.domain.Payment.Card].name`

the thing above is basically a path to the field/subtype under which `ducktape` was not able to create a transformation, these are meant to be copy-pastable for when you're actually trying to fix the error, eg. by setting the `name` field to a constant value:

```scala
val domainPerson = 
  wirePerson
    .into[domain.Person]
    .transform(Field.const(_.paymentMethods.element.at[domain.Payment.Card].name, "CONST NAME"))
// domainPerson: Person = Person(
//   lastName = "Doe",
//   firstName = "John",
//   status = Some(value = PendingRegistration),
//   paymentMethods = Vector(
//     Cash,
//     PayPal(email = "john@doe.com"),
//     Card(
//       name = "CONST NAME",
//       digits = 12345L,
//       expires = 2024-03-10T00:21:33.864184449Z
//     )
//   ),
//   updatedAt = Some(value = 1970-01-01T00:00:00Z)
// )
```

<details>
  <summary>Click to see the generated code</summary>
  
``` scala 
  {
    val AppliedBuilder_this: AppliedBuilder[Person, Person] = into[Person](MdocApp2.this.wirePerson1)[MdocApp2.this.domain.Person]

    {
      val value$proxy3: Person = AppliedBuilder_this.inline$value

      {
        val paymentMethods$4: Vector[Payment] = value$proxy3.paymentMethods
          .map[Payment]((src: PaymentMethod) =>
            if (src.isInstanceOf[Card])
              new Card(name = "CONST NAME", digits = src.asInstanceOf[Card].digits, expires = src.asInstanceOf[Card].expires)
            else if (src.isInstanceOf[PayPal]) new PayPal(email = src.asInstanceOf[PayPal].email)
            else if (src.isInstanceOf[Cash.type]) MdocApp2.this.domain.Payment.Cash
            else throw new RuntimeException("Unhandled case. This is most likely a bug in ducktape.")
          )
          .to[Vector[Payment]](iterableFactory[Payment])
        val status$4: Some[Status] = Some.apply[Status](
          if (value$proxy3.status.isInstanceOf[Registered.type]) MdocApp2.this.domain.Status.Registered
          else if (value$proxy3.status.isInstanceOf[PendingRegistration.type]) MdocApp2.this.domain.Status.PendingRegistration
          else if (value$proxy3.status.isInstanceOf[Removed.type]) MdocApp2.this.domain.Status.Removed
          else throw new RuntimeException("Unhandled case. This is most likely a bug in ducktape.")
        )
        new Person(
          lastName = value$proxy3.lastName,
          firstName = value$proxy3.firstName,
          status = status$4,
          paymentMethods = paymentMethods$4,
          updatedAt = value$proxy3.updatedAt
        )
      }: Person
    }: Person
  }
```
</details>
