# ![ducktape-logo-32](https://user-images.githubusercontent.com/46346508/236060869-3b118075-f660-44c9-9d0d-d40fba5c8db0.svg) ducktape 0.2.x

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.github.arainko/ducktape_3/badge.svg?style=flat-square)](https://maven-badges.herokuapp.com/maven-central/io.github.arainko/ducktape_3)

*ducktape* is a library for boilerplate-less and configurable transformations between case classes and enums/sealed traits for Scala 3. Directly inspired by [chimney](https://github.com/scalalandio/chimney).

If this project interests you, please drop a 🌟 - these things are worthless but give me a dopamine rush nonetheless.

## Table of contents

* [Installation](#installation)
* [Motivating example](#motivating-example)
* [Basics](#basics)
* [Configuring transformations](#configuring-transformations)
  * [Introduction and explanation](#introduction-and-explanation)
  * [Product configurations](#product-configurations)
  * [Coproduct configurations](#coproduct-configurations)
  * [Specifics and limitations](#specifics-and-limitations)
* [Fallible transfomations](#fallible-transfomations)
  * [Definition of `Transformer.Fallible` and `Mode`](#definition-of-transformerfallible-and-mode)
  * [Making the most out of `Fallible Transformers`](#making-the-most-out-of-fallible-transformers)
  * [Configuring fallible transformations](#configuring-fallible-transformations)
  * [Fallible product configurations](#fallible-product-configurations)
  * [Fallible coproduct configurations](#fallible-coproduct-configurations)
  * [Building custom instances of fallible transformers](#building-custom-instances-of-fallible-transformers)
* [Transfomation rules](#transfomation-rules)
* [Coming from 0.1.x](#coming-from-01x)

## Installation
```scala
libraryDependencies += "io.github.arainko" %% "ducktape" % "0.2.0-M4"

// or if you're using Scala.js or Scala Native
libraryDependencies += "io.github.arainko" %%% "ducktape" % "0.2.0-M4"
```

NOTE: the [version scheme](https://www.scala-lang.org/blog/2021/02/16/preventing-version-conflicts-with-versionscheme.html) is set to `early-semver`

You're currently browsing the documentation for `ducktape 0.2.x`, if you're looking for the `0.1.x` docs go here: https://github.com/arainko/ducktape/tree/series/0.1.x#-ducktape

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

## Basics

```scala
// Entrypoint to the library
import io.github.arainko.ducktape.*
```

The import above brings in a number of extension methods, let's examine how these work by redefining a simplified version of the model first seen in the [motivating example](#motivating-example):

```scala
object wire {
  final case class Person(
    firstName: String,
    lastName: String,
    paymentMethods: List[wire.PaymentMethod],
  )

  enum PaymentMethod:
    case Card(name: String, digits: Long)
    case PayPal(email: String)
    case Cash
}

object domain {
  final case class Person(
    firstName: String,
    lastName: String,
    paymentMethods: Vector[domain.PaymentMethod],
  )

  enum PaymentMethod:
    case Card(name: String, digits: Long)
    case PayPal(email: String)
    case Cash
}

val wirePerson = wire.Person("John", "Doe", 
  List(
    wire.PaymentMethod.Cash,
    wire.PaymentMethod.PayPal("john@doe.com"),
    wire.PaymentMethod.Card("J. Doe", 23232323)
  )
)

```

* `Source#to[Dest]` - for any two types `Source` and `Dest`, used to create a direct transformation between `Source` and `Dest`:
```scala
import io.github.arainko.ducktape.*

wirePerson.to[domain.Person]
// res6: Person = Person(
//   firstName = "John",
//   lastName = "Doe",
//   paymentMethods = Vector(
//     Cash,
//     PayPal(email = "john@doe.com"),
//     Card(name = "J. Doe", digits = 23232323L)
//   )
// )
```
<details>
  <summary>Click to see the generated code</summary>

``` scala 
  ((new Person(
    firstName = MdocApp5.this.wirePerson.firstName,
    lastName = MdocApp5.this.wirePerson.lastName,
    paymentMethods = MdocApp5.this.wirePerson.paymentMethods
      .map[PaymentMethod]((src: PaymentMethod) =>
        if (src.isInstanceOf[Card]) new Card(name = src.asInstanceOf[Card].name, digits = src.asInstanceOf[Card].digits)
        else if (src.isInstanceOf[PayPal]) new PayPal(email = src.asInstanceOf[PayPal].email)
        else if (src.isInstanceOf[Cash.type]) MdocApp5.this.domain.PaymentMethod.Cash
        else throw new RuntimeException("Unhandled case. This is most likely a bug in ducktape.")
      )
      .to[Vector[PaymentMethod]](iterableFactory[PaymentMethod])
  ): Person): Person)
```
</details>

Read more about the rules under which the transformations are generated in ['Transformation rules'](#transfomation-rules).

* `Source#into[Dest]` -  for any two types `Source` and `Dest`, used to create a 'transformation builder' that allows fixing transformation errors and overriding transformations for selected fields or subtypes.

```scala
import io.github.arainko.ducktape.*

wirePerson
  .into[domain.Person]
  .transform(Field.const(_.paymentMethods.element.at[domain.PaymentMethod.PayPal].email, "overridden@email.com"))
// res8: Person = Person(
//   firstName = "John",
//   lastName = "Doe",
//   paymentMethods = Vector(
//     Cash,
//     PayPal(email = "overridden@email.com"),
//     Card(name = "J. Doe", digits = 23232323L)
//   )
// )
```

<details>
  <summary>Click to see the generated code</summary>

``` scala 
  {
    val AppliedBuilder_this: AppliedBuilder[Person, Person] = into[Person](MdocApp5.this.wirePerson)[MdocApp5.this.domain.Person]

    {
      val value$proxy7: Person = AppliedBuilder_this.inline$value

      new Person(
        firstName = value$proxy7.firstName,
        lastName = value$proxy7.lastName,
        paymentMethods = value$proxy7.paymentMethods
          .map[PaymentMethod]((src: PaymentMethod) =>
            if (src.isInstanceOf[Card]) new Card(name = src.asInstanceOf[Card].name, digits = src.asInstanceOf[Card].digits)
            else if (src.isInstanceOf[PayPal]) new PayPal(email = "overridden@email.com")
            else if (src.isInstanceOf[Cash.type]) MdocApp5.this.domain.PaymentMethod.Cash
            else throw new RuntimeException("Unhandled case. This is most likely a bug in ducktape.")
          )
          .to[Vector[PaymentMethod]](iterableFactory[PaymentMethod])
      ): Person
    }: Person
  }
```
</details>

Read more in the section about [configuring transformations](#configuring-transformations).

* `Source#via(<method reference>)` - for any type `Source` and a `method reference` that can be eta-expanded into a function with named arguments, used to expand the method's argument list with the fields of the `Source` type

```scala
import io.github.arainko.ducktape.*

wirePerson.via(domain.Person.apply)
// res10: Person = Person(
//   firstName = "John",
//   lastName = "Doe",
//   paymentMethods = Vector(
//     Cash,
//     PayPal(email = "john@doe.com"),
//     Card(name = "J. Doe", digits = 23232323L)
//   )
// )
```
<details>
  <summary>Click to see the generated code</summary>

``` scala 
  {
    val firstName: String = MdocApp5.this.wirePerson.firstName
    val lastName: String = MdocApp5.this.wirePerson.lastName
    val paymentMethods: Vector[PaymentMethod] = MdocApp5.this.wirePerson.paymentMethods
      .map[PaymentMethod]((src: PaymentMethod) =>
        if (src.isInstanceOf[Card]) new Card(name = src.asInstanceOf[Card].name, digits = src.asInstanceOf[Card].digits)
        else if (src.isInstanceOf[PayPal]) new PayPal(email = src.asInstanceOf[PayPal].email)
        else if (src.isInstanceOf[Cash.type]) MdocApp5.this.domain.PaymentMethod.Cash
        else throw new RuntimeException("Unhandled case. This is most likely a bug in ducktape.")
      )
      .to[Vector[PaymentMethod]](iterableFactory[PaymentMethod])
    MdocApp5.this.domain.Person.apply(firstName, lastName, paymentMethods)
  }
```
</details>

To read about how these transformations are generated head on over to the section about [transformation rules](#transfomation-rules).

* `Source.intoVia(<method reference>)` - for any type `Source` and a `method reference` that can be eta-expanded into a function with named arguments, used to create a 'transformation builder' that allows fixing transformation errors and overriding transformations for selected fields or subtypes.

```scala
import io.github.arainko.ducktape.*

wirePerson
  .intoVia(domain.Person.apply)
  .transform(Field.const(_.paymentMethods.element.at[domain.PaymentMethod.PayPal].email, "overridden@email.com"))
// res12: Person = Person(
//   firstName = "John",
//   lastName = "Doe",
//   paymentMethods = Vector(
//     Cash,
//     PayPal(email = "overridden@email.com"),
//     Card(name = "J. Doe", digits = 23232323L)
//   )
// )
```

<details>
  <summary>Click to see the generated code</summary>

``` scala 
  {
    val inst: AppliedViaBuilder[Person, Nothing, Function3[String, String, Vector[PaymentMethod], Person], Nothing] =
      inline$instance[Person, Function3[String, String, Vector[PaymentMethod], Person]](
        MdocApp5.this.wirePerson,
        (firstName: String, lastName: String, paymentMethods: Vector[PaymentMethod]) =>
          MdocApp5.this.domain.Person.apply(firstName, lastName, paymentMethods)
      )
    val AppliedViaBuilder_this: AppliedViaBuilder[
      Person,
      Person,
      Function3[String, String, Vector[PaymentMethod], Person],
      FunctionArguments {
        val firstName: String
        val lastName: String
        val paymentMethods: Vector[PaymentMethod]
      }
    ] = inst.asInstanceOf[[args >: Nothing <: FunctionArguments, retTpe >: Nothing <: Any] =>> AppliedViaBuilder[
      Person,
      retTpe,
      Function3[String, String, Vector[PaymentMethod], Person],
      args
    ][
      FunctionArguments {
        val firstName: String
        val lastName: String
        val paymentMethods: Vector[PaymentMethod]
      },
      Person
    ]]

    {
      val value$proxy11: Person = AppliedViaBuilder_this.inline$value
      val function$proxy20: Function3[String, String, Vector[PaymentMethod], Person] = AppliedViaBuilder_this.inline$function

      function$proxy20.apply(
        value$proxy11.firstName,
        value$proxy11.lastName,
        value$proxy11.paymentMethods
          .map[PaymentMethod]((src: PaymentMethod) =>
            if (src.isInstanceOf[Card]) new Card(name = src.asInstanceOf[Card].name, digits = src.asInstanceOf[Card].digits)
            else if (src.isInstanceOf[PayPal]) new PayPal(email = "overridden@email.com")
            else if (src.isInstanceOf[Cash.type]) MdocApp5.this.domain.PaymentMethod.Cash
            else throw new RuntimeException("Unhandled case. This is most likely a bug in ducktape.")
          )
          .to[Vector[PaymentMethod]](iterableFactory[PaymentMethod])
      ): Person
    }: Person
  }
```
</details>

Read more in the section about [configuring transformations](#configuring-transformations).

## Configuring transformations

### Introduction and explanation

Transformations can customized or 'fixed' with a slew of configuration options, let's examine a quick example based on a slightly modified version of the previously introduced model:

```scala
import io.github.arainko.ducktape.*

object wire {
  final case class Person(
    firstName: String,
    lastName: String,
    paymentMethods: List[wire.PaymentMethod],
  )

  enum PaymentMethod:
    case Card(name: String, digits: Long)
    case PayPal(email: String)
    case Cash
    case Transfer(accountNo: String) // <-- additional enum case, not present in the domain model
}

object domain {
  final case class Person(
    firstName: String,
    lastName: String,
    age: Int, // <-- additional field, not present in the wire model
    paymentMethods: Vector[domain.PaymentMethod],
  )

  enum PaymentMethod:
    case Card(name: String, digits: Long)
    case PayPal(email: String)
    case Cash
}

val wirePerson = wire.Person("John", "Doe", 
  List(
    wire.PaymentMethod.Cash,
    wire.PaymentMethod.PayPal("john@doe.com"),
    wire.PaymentMethod.Card("J. Doe", 23232323),
    wire.PaymentMethod.Transfer("21371284583271927489486")
  )
)
```

Right off the bat the compiler yells at for trying to transform into a `domain.Person` for two reasons:
```scala
wirePerson.to[domain.Person]
// error:
// No child named 'Transfer' found in repl.MdocSession.MdocApp2.domain.PaymentMethod @ Person.paymentMethods.element.at[repl.MdocSession.MdocApp2.wire.PaymentMethod.Transfer]
// No field 'age' found in repl.MdocSession.MdocApp2.wire.Person @ Person.age
//   Docs.printCode(1.to[Wrapper2])
// ^
```

The newly added field (`age`) and enum case (`PaymentMethod.Transfer`) do not have a corresponding mapping, let's say we want to set the age field to a constant value of 24 and when a PaymentMethod.Transfer is encountered we map it to `Cash` instead.

```scala
wirePerson
  .into[domain.Person]
  .transform(
    Field.const(_.age, 24),
    Case.const(_.paymentMethods.element.at[wire.PaymentMethod.Transfer], domain.PaymentMethod.Cash)
  )
// res16: Person = Person(
//   firstName = "John",
//   lastName = "Doe",
//   age = 24,
//   paymentMethods = Vector(
//     Cash,
//     PayPal(email = "john@doe.com"),
//     Card(name = "J. Doe", digits = 23232323L),
//     Cash
//   )
// )
```
<details>
  <summary>Click to see the generated code</summary>

``` scala 
  {
    val AppliedBuilder_this: AppliedBuilder[Person, Person] = into[Person](wirePerson)[domain.Person]

    {
      val value$proxy14: Person = AppliedBuilder_this.inline$value

      new Person(
        firstName = value$proxy14.firstName,
        lastName = value$proxy14.lastName,
        age = 24,
        paymentMethods = value$proxy14.paymentMethods
          .map[PaymentMethod]((src: PaymentMethod) =>
            if (src.isInstanceOf[Card]) new Card(name = src.asInstanceOf[Card].name, digits = src.asInstanceOf[Card].digits)
            else if (src.isInstanceOf[PayPal]) new PayPal(email = src.asInstanceOf[PayPal].email)
            else if (src.isInstanceOf[Cash.type]) Cash
            else if (src.isInstanceOf[Transfer]) domain.PaymentMethod.Cash
            else throw new RuntimeException("Unhandled case. This is most likely a bug in ducktape.")
          )
          .to[Vector[PaymentMethod]](iterableFactory[PaymentMethod])
      ): Person
    }: Person
  }
```
</details>

Great! But let's take a step back and examine what we just did, starting with the first config example:
```scala
Field.const(_.age, 24)
            |      |
            |      the second argument is the constant itself, whatever value is passed here needs to be a subtype of the field type 
            the first argument is the path to the field we're configuring (all Field configs operate on the destination type)        
```

and now for the second one:

```scala
Case.const(_.paymentMethods.element.at[wire.PaymentMethod.Transfer], domain.PaymentMethod.Cash)
              |             |       |
              |             |       '.at' is another special case used to pick a subtype of an enum/sealed trait
              |             '.element' is a special extension method that allows us to configure the type inside a collection or an Option
              path expressions are not limited to a single field, we can use these to dive as deep as we need for our config to be (paths inside Case configs operate on the source type)
```

So, is `.at` and `.element` another one of those extensions that will always pollute the namespace? Thankfully, no - let's look at how `Field.const` and `Case.const` are actually defined in the code:

```scala
opaque type Field[Source, Dest] = Unit

object Field {
  @compileTimeOnly("Field.const is only useable as a field configuration for transformations")
  def const[Source, Dest, DestFieldTpe, ConstTpe](path: Selector ?=> Dest => DestFieldTpe, value: ConstTpe): Field[Source, Dest] = ???
}

opaque type Case[A, B] = Unit

object Case {
  @compileTimeOnly("Case.const is only useable as a case configuration for transformations")
  def const[Source, Dest, SourceTpe, ConstTpe](path: Selector ?=> Source => SourceTpe, value: ConstTpe): Case[Source, Dest] = ???
}
```

the things that interest us the most are the `path` paramenters of both of these methods, defined as a context function of `Selector` to a function that allows us to 'pick' which part of the transformation we want to customize.

So what is a `Selector` anyway? It is defined as such:

```scala
sealed trait Selector {
  extension [A](self: A) def at[B <: A]: B

  extension [Elem](self: Iterable[Elem] | Option[Elem]) def element: Elem
}
```

Which means that for a context function such as `Selector ?=> Dest => DestFieldTpe` the `Selector` brings in the neccessary extensions that allow us to pick and configure subtypes and elements under a collection or an `Option`, but only in the scope of that context function and not anywhere outside which means we do not pollute the outside world's namespace with these.

What's worth noting is that any of the configuration options are purely a compiletime construct and are completely erased from the runtime representation (i.e. it's not possible to implement an instance of a `Selector` in a sane way since such an implementation would throw exceptions left and right but using it as a sort of a DSL for picking and choosing is completely fair game since it doesn't exist at runtime).

### Product configurations

Let's introduce another payment method (not part of any of the previous payment method ADTs, just a standalone case class).

```scala
case class PaymentBand(name: String, digits: Long, color: String = "red")

val card: wire.PaymentMethod.Card = 
  wire.PaymentMethod.Card(name = "J. Doe", digits = 213712345)
```

* `Field.const` - allows to supply a constant value for a given field

```scala
card
  .into[PaymentBand]
  .transform(Field.const(_.color, "blue"))
// res18: PaymentBand = PaymentBand(
//   name = "J. Doe",
//   digits = 213712345L,
//   color = "blue"
// )
```
<details>
  <summary>Click to see the generated code</summary>

``` scala 
  {
    val AppliedBuilder_this: AppliedBuilder[Card, PaymentBand] = into[Card](card)[PaymentBand]

    {
      val value$proxy17: Card = AppliedBuilder_this.inline$value

      new PaymentBand(name = value$proxy17.name, digits = value$proxy17.digits, color = "blue"): PaymentBand
    }: PaymentBand
  }
```
</details>

* `Field.computed` - allows to compute a value with a function the shape of `Dest => FieldTpe`
```scala
card
  .into[PaymentBand]
  .transform(
    Field.computed(_.color, card => if (card.digits % 2 == 0) "green" else "yellow")
  )
// res20: PaymentBand = PaymentBand(
//   name = "J. Doe",
//   digits = 213712345L,
//   color = "yellow"
// )
```
<details>
  <summary>Click to see the generated code</summary>

``` scala 
  {
    val AppliedBuilder_this: AppliedBuilder[Card, PaymentBand] = into[Card](card)[PaymentBand]

    {
      val value$proxy20: Card = AppliedBuilder_this.inline$value

      new PaymentBand(
        name = value$proxy20.name,
        digits = value$proxy20.digits,
        color = if (value$proxy20.digits.%(2).==(0)) "green" else "yellow"
      ): PaymentBand
    }: PaymentBand
  }
```
</details>

* `Field.default` - only works when a field's got a default value defined (defaults are not taken into consideration by default)

```scala
card
  .into[PaymentBand]
  .transform(Field.default(_.color))
// res22: PaymentBand = PaymentBand(
//   name = "J. Doe",
//   digits = 213712345L,
//   color = "red"
// )
```
<details>
  <summary>Click to see the generated code</summary>

``` scala 
  {
    val AppliedBuilder_this: AppliedBuilder[Card, PaymentBand] = into[Card](card)[PaymentBand]

    {
      val value$proxy23: Card = AppliedBuilder_this.inline$value

      new PaymentBand(
        name = value$proxy23.name,
        digits = value$proxy23.digits,
        color = PaymentBand.$lessinit$greater$default$3
      ): PaymentBand
    }: PaymentBand
  }
```
</details>

* `Field.allMatching` - allow to supply a field source whose fields will replace all matching fields in the destination (given that the names and the types match up)

```scala
case class FieldSource(color: String, digits: Long, extra: Int)
val source = FieldSource("magenta", 123445678, 23)
```

```scala
card
  .into[PaymentBand]
  .transform(Field.allMatching(paymentBand => paymentBand, source))
// res24: PaymentBand = PaymentBand(
//   name = "J. Doe",
//   digits = 123445678L,
//   color = "magenta"
// )
```
<details>
  <summary>Click to see the generated code</summary>

``` scala 
  {
    val AppliedBuilder_this: AppliedBuilder[Card, PaymentBand] = into[Card](card)[PaymentBand]

    {
      val value$proxy26: Card = AppliedBuilder_this.inline$value

      new PaymentBand(name = value$proxy26.name, digits = source.digits, color = source.color): PaymentBand
    }: PaymentBand
  }
```
</details>

* `Field.fallbackToDefault` - falls back to default field values but ONLY in case a transformation cannot be created
```scala
case class SourceToplevel(level1: SourceLevel1, transformableButWithDefault: Int)
case class SourceLevel1(str: String)

case class DestToplevel(level1: DestLevel1, extra: Int = 111, transformableButWithDefault: Int = 3000)
case class DestLevel1(extra: String = "level1", str: String)

val source = SourceToplevel(SourceLevel1("str"), 400)
```
```scala
source
  .into[DestToplevel]
  .transform(Field.fallbackToDefault)
// res26: DestToplevel = DestToplevel(
//   level1 = DestLevel1(extra = "level1", str = "str"),
//   extra = 111,
//   transformableButWithDefault = 400
// )
```

<details>
  <summary>Click to see the generated code</summary>

``` scala 
  {
    val AppliedBuilder_this: AppliedBuilder[SourceToplevel, DestToplevel] = into[SourceToplevel](source)[DestToplevel]

    {
      val value$proxy29: SourceToplevel = AppliedBuilder_this.inline$value

      new DestToplevel(
        level1 = new DestLevel1(extra = DestLevel1.$lessinit$greater$default$1, str = value$proxy29.level1.str),
        extra = DestToplevel.$lessinit$greater$default$2,
        transformableButWithDefault = value$proxy29.transformableButWithDefault
      ): DestToplevel
    }: DestToplevel
  }
```
</details>

`Field.fallbackToDefault` is a `regional` config, which means that you can control the scope where it applies:

```scala
source
  .into[DestToplevel]
  .transform(
    Field.fallbackToDefault.regional(_.level1), // <-- we're applying the config starting on the `.level1` field and below, it'll be also applied to other transformations nested inside
    Field.const(_.extra, 123) // <-- note that this field now needs to be configured manually
  )
// res28: DestToplevel = DestToplevel(
//   level1 = DestLevel1(extra = "level1", str = "str"),
//   extra = 123,
//   transformableButWithDefault = 400
// )
```

<details>
  <summary>Click to see the generated code</summary>

``` scala 
  {
    val AppliedBuilder_this: AppliedBuilder[SourceToplevel, DestToplevel] = into[SourceToplevel](source)[DestToplevel]

    {
      val value$proxy32: SourceToplevel = AppliedBuilder_this.inline$value

      new DestToplevel(
        level1 = new DestLevel1(extra = DestLevel1.$lessinit$greater$default$1, str = value$proxy32.level1.str),
        extra = 123,
        transformableButWithDefault = value$proxy32.transformableButWithDefault
      ): DestToplevel
    }: DestToplevel
  }
```
</details>

* `Field.fallbackToNone` - falls back to `None` for `Option` fields for which a transformation cannot be created

```scala
case class SourceToplevel(level1: SourceLevel1, transformable: Option[Int])
case class SourceLevel1(str: String)

case class DestToplevel(level1: DestLevel1, extra: Option[Int], transformable: Option[Int])
case class DestLevel1(extra: Option[String], str: String)

val source = SourceToplevel(SourceLevel1("str"), Some(400))
```

```scala
source
  .into[DestToplevel]
  .transform(Field.fallbackToNone)
// res30: DestToplevel = DestToplevel(
//   level1 = DestLevel1(extra = None, str = "str"),
//   extra = None,
//   transformable = Some(value = 400)
// )
```

<details>
  <summary>Click to see the generated code</summary>

``` scala 
  {
    val AppliedBuilder_this: AppliedBuilder[SourceToplevel, DestToplevel] = into[SourceToplevel](source)[DestToplevel]

    {
      val value$proxy35: SourceToplevel = AppliedBuilder_this.inline$value

      new DestToplevel(
        level1 = new DestLevel1(extra = None, str = value$proxy35.level1.str),
        extra = None,
        transformable = value$proxy35.transformable
      ): DestToplevel
    }: DestToplevel
  }
```
</details>

`Field.fallbackToNone` is a `regional` config, which means that you can control the scope where it applies:

```scala
source
  .into[DestToplevel]
  .transform(
    Field.fallbackToNone.regional(_.level1), // <-- we're applying the config starting on the `.level1` field and below, it'll be also applied to other transformations nested inside
    Field.const(_.extra, Some(123)) // <-- note that this field now needs to be configured manually
  )
// res32: DestToplevel = DestToplevel(
//   level1 = DestLevel1(extra = None, str = "str"),
//   extra = Some(value = 123),
//   transformable = Some(value = 400)
// )
```

<details>
  <summary>Click to see the generated code</summary>

``` scala 
  {
    val AppliedBuilder_this: AppliedBuilder[SourceToplevel, DestToplevel] = into[SourceToplevel](source)[DestToplevel]

    {
      val value$proxy38: SourceToplevel = AppliedBuilder_this.inline$value

      new DestToplevel(
        level1 = new DestLevel1(extra = None, str = value$proxy38.level1.str),
        extra = Some.apply[Int](123),
        transformable = value$proxy38.transformable
      ): DestToplevel
    }: DestToplevel
  }
```
</details>

### Coproduct configurations

```scala
val transfer = wire.PaymentMethod.Transfer("2764262")
// transfer: PaymentMethod = Transfer(accountNo = "2764262")
```

* `Case.const` - allows to supply a constant value for a given subtype of a coproduct
```scala
transfer
  .into[domain.PaymentMethod]
  .transform(Case.const(_.at[wire.PaymentMethod.Transfer], domain.PaymentMethod.Cash))
// res34: PaymentMethod = Cash
```
<details>
  <summary>Click to see the generated code</summary>

``` scala 
  {
    val AppliedBuilder_this: AppliedBuilder[PaymentMethod, PaymentMethod] = into[PaymentMethod](transfer)[domain.PaymentMethod]

    {
      val value$proxy41: PaymentMethod = AppliedBuilder_this.inline$value

      if (value$proxy41.isInstanceOf[Card])
        new Card(name = value$proxy41.asInstanceOf[Card].name, digits = value$proxy41.asInstanceOf[Card].digits)
      else if (value$proxy41.isInstanceOf[PayPal]) new PayPal(email = value$proxy41.asInstanceOf[PayPal].email)
      else if (value$proxy41.isInstanceOf[Cash.type]) Cash
      else if (value$proxy41.isInstanceOf[Transfer]) domain.PaymentMethod.Cash
      else throw new RuntimeException("Unhandled case. This is most likely a bug in ducktape."): PaymentMethod
    }: PaymentMethod
  }
```
</details>


* `Case.computed` - allow to supply a function of the selected source type to the expected destination type
```scala
transfer
  .into[domain.PaymentMethod]
  .transform(
    Case.computed(_.at[wire.PaymentMethod.Transfer], transfer => domain.PaymentMethod.Card("J. Doe", transfer.accountNo.toLong))
  )
// res36: PaymentMethod = Card(name = "J. Doe", digits = 2764262L)
```

<details>
  <summary>Click to see the generated code</summary>

``` scala 
  {
    val AppliedBuilder_this: AppliedBuilder[PaymentMethod, PaymentMethod] = into[PaymentMethod](transfer)[domain.PaymentMethod]

    {
      val value$proxy44: PaymentMethod = AppliedBuilder_this.inline$value

      if (value$proxy44.isInstanceOf[Card])
        new Card(name = value$proxy44.asInstanceOf[Card].name, digits = value$proxy44.asInstanceOf[Card].digits)
      else if (value$proxy44.isInstanceOf[PayPal]) new PayPal(email = value$proxy44.asInstanceOf[PayPal].email)
      else if (value$proxy44.isInstanceOf[Cash.type]) Cash
      else if (value$proxy44.isInstanceOf[Transfer]) {
        val `transfer₂` : Transfer = value$proxy44.asInstanceOf[Transfer]

        domain.PaymentMethod.Card.apply("J. Doe", augmentString(`transfer₂`.accountNo).toLong): PaymentMethod
      } else throw new RuntimeException("Unhandled case. This is most likely a bug in ducktape."): PaymentMethod
    }: PaymentMethod
  }
```
</details>

### Specifics and limitations

* Configs can override transformations
```scala
wirePerson
  .into[domain.Person]
  .transform(
    Field.const(_.age, 24),
    Case.const(_.paymentMethods.element.at[wire.PaymentMethod.Transfer], domain.PaymentMethod.Cash),
    Field.const(_.paymentMethods.element, domain.PaymentMethod.Cash) // <-- override all payment methods to `Cash`
  )
// res38: Person = Person(
//   firstName = "John",
//   lastName = "Doe",
//   age = 24,
//   paymentMethods = Vector(Cash, Cash, Cash, Cash)
// )
```

* Configs can override each other

```scala
wirePerson
  .into[domain.Person]
  .transform(
    Case.const(_.paymentMethods.element.at[wire.PaymentMethod.Transfer], domain.PaymentMethod.Cash),
    Field.const(_.age, 24),
    Field.const(_.age, 50) // <-- override the previously configured 'age' field`
  )
// res39: Person = Person(
//   firstName = "John",
//   lastName = "Doe",
//   age = 50,
//   paymentMethods = Vector(
//     Cash,
//     PayPal(email = "john@doe.com"),
//     Card(name = "J. Doe", digits = 23232323L),
//     Cash
//   )
// )
```

* Config on a field or a case 'above' overrides the configs 'below'
```scala
wirePerson
  .into[domain.Person]
  .transform(
    Field.const(_.age, 24),
    Case.const(_.paymentMethods.element.at[wire.PaymentMethod.Transfer], domain.PaymentMethod.Cash),
    Field.const(_.paymentMethods.element, domain.PaymentMethod.Cash), // <-- override all payment methods to `Cash`,
    Field.const(_.paymentMethods, Vector.empty[domain.PaymentMethod]) // <-- also override the 'parent' of '_.paymentMethods.element' so now payment methods are just empty
  )
// res40: Person = Person(
//   firstName = "John",
//   lastName = "Doe",
//   age = 24,
//   paymentMethods = Vector()
// )
```

However, first configuring the field a level above and then the field a level below is not supported:
```scala
wirePerson
  .into[domain.Person]
  .transform(
    Field.const(_.age, 24),
    Case.const(_.paymentMethods.element.at[wire.PaymentMethod.Transfer], domain.PaymentMethod.Cash),
    Field.const(_.paymentMethods, Vector.empty[domain.PaymentMethod]), // <-- configure the field a level above first
    Field.const(_.paymentMethods.element, domain.PaymentMethod.Cash), // <-- then the field below it
  )
// error:
// The path segment 'element' is not valid @ Person.paymentMethods
//   import io.github.arainko.ducktape.docs.*
//  ^
```

## Fallible transfomations
Sometimes ordinary field mappings just do not cut it, more often than not our domain model's constructors are hidden behind a safe factory method, eg.:

```scala
import io.github.arainko.ducktape.*

final case class Person private (name: String, age: Int)

object Person {
  def create(name: String, age: Int): Either[String, Person] =
    for {
      validatedName <- Either.cond(!name.isBlank, name, "Name should not be blank")
      validatedAge  <- Either.cond(age > 0, age, "Age should be positive")
    } yield Person(validatedName, validatedAge)
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

val transformed = unvalidatedPerson.via(Person.create)
// transformed: Either[String, Person] = Left(value = "Age should be positive")
```

But this quickly falls apart when nested transformations are introduced and we're pretty much back to square one where we're on our own to write the boilerplate.

That's where `Fallible Transformers` and their modes come in: 
* `Mode.Accumulating` for error accumulation,
* `Mode.FailFast` for the cases where we just want to bail at the very first sight of trouble.

Let's look at the definition of all of these:

### Definition of `Transformer.Fallible` and `Mode`

```scala
object Transformer {
  trait Fallible[F[+x], Source, Dest] {
    def transform(value: Source): F[Dest]
  }
}
```
So a `Fallible` transformer takes a `Source` and gives back a `Dest` wrapped in an `F` where `F` is the wrapper type for our transformations eg. if `F[+x]` = `Either[List[String], x]` then the `transform` method will return an `Either[List[String], Dest]`.

```scala
sealed trait Mode[F[+x]] {
  def pure[A](value: A): F[A]

  def map[A, B](fa: F[A], f: A => B): F[B]

  def traverseCollection[A, B, AColl <: Iterable[A], BColl <: Iterable[B]](
    collection: AColl,
    transformation: A => F[B]
  )(using factory: Factory[B, BColl]): F[BColl]
}
```

Moving on to `Mode`, what exactly is it and why do we need it? So a `Mode[F]` is typeclass that gives us two bits of information:
* a hint for the derivation mechanism which transformation mode to use (hence the name!)
* some operations on the abstract `F` wrapper type, namely:
  * `pure` is for wrapping arbitrary values into `F`, eg. if `F[+x] = Either[List[String], x]` then calling `pure` would involve just wrapping the value in a `Right.apply` call.
  * `map` is for operating on the wrapped values, eg. if we find ourselves with a `F[Int]` in hand and we want to transform the value 'inside' to a `String` we can call `.map(_.toString)` to yield a `F[String]`
  * `traverseCollection` is for the cases where we end up with a collection of wrapped values (eg. a `List[F[String]]`) and we want to transform that into a `F[List[String]]` according to the rules of the `F` type wrapper and not blow up the stack in the process

As mentioned earlier, `Modes` come in two flavors - one for error accumulating transformations (`Mode.Accumulating[F]`) and one for fail fast transformations (`Mode.FailFast[F]`):

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

Each one of these exposes one operation that dictates its approach to errors, `flatMap` entails a dependency between fallible transformations so if we chain multiple `flatMaps` together our transformation will stop at the very first error - contrary to this, `Mode.Accumulating` exposes a `product` operation that given two independent transformations wrapped in an `F` gives us back a tuple wrapped in an `F`. What that really means is that each transformation is independent from one another so we're able to accumulate all of the errors produced by these.

For accumulating transformations `ducktape` provides instances for `Either` with any subtype of `Iterable` on the left side, so that eg. `Mode.Accumulating[[A] =>> Either[List[String], A]]` is available out of the box (under the subclass of `Mode.Accumulating.Either[String, List]`).

For fail fast transformations, instances for `Option` (`Mode.FailFast.Option`) and `Either` (`Mode.FailFast.Either`) are avaiable out of the box.

### Making the most out of `Fallible Transformers`

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

Now let's get back to the definition of `Person` and tweak it a little:

```scala
case class Person(name: Name, age: Age, socialSecurityNo: SSN)

object Name extends NewtypeValidated[String](str => !str.isBlank, "Name should not be blank!")
type Name = Name.Type

object Age extends NewtypeValidated[Int](int => int > 0, "Age should be positive!")
type Age = Age.Type

object SSN extends NewtypeValidated[String](str => str.length > 5, "SSN should be longer than 5!")
type SSN = SSN.Type
```

We introduce a newtype for each field, this way we can keep our invariants at compiletime and also let `ducktape` do its thing.

```scala
// this should trip up our validation
val bad = UnvalidatedPerson(name = "", age = -1, socialSecurityNo = "SOCIALNO")

// this one should pass
val good = UnvalidatedPerson(name = "ValidName", age = 24, socialSecurityNo = "SOCIALNO")
```

Instances of `Transformer.Fallible` wrapped in some type `F` are derived automatically for case classes given that a `Mode.Accumulating` instance exists for `F` and all of the fields of the source type have a corresponding counterpart in the destination type and each one of them has an instance of either `Transformer.Fallible` or a total `Transformer` in scope.

```scala
given Mode.Accumulating.Either[String, List] with {}

bad.fallibleTo[Person]
// res43: Either[List[String], Person] = Left(
//   value = List("Name should not be blank!", "Age should be positive!")
// )
good.fallibleTo[Person]
// res44: Either[List[String], Person] = Right(
//   value = Person(name = "ValidName", age = 24, socialSecurityNo = "SOCIALNO")
// )
```

<details>
  <summary>Click to see the generated code</summary>

``` scala 
  ((given_Either_String_List.map[Tuple2[Type, Tuple2[Type, Type]], Person](
    given_Either_String_List.product[Type, Tuple2[Type, Type]](
      Name.accumulatingWrappingTransformer.transform(bad.name),
      given_Either_String_List.product[Type, Type](
        Age.accumulatingWrappingTransformer.transform(bad.age),
        SSN.accumulatingWrappingTransformer.transform(bad.socialSecurityNo)
      )
    ),
    (value: Tuple2[Type, Tuple2[Type, Type]]) => new Person(name = value._1, age = value._2._1, socialSecurityNo = value._2._2)
  ): Either[List[String], Person]): Either[List[String], Person])
```
</details>

Same goes for instances that do fail fast transformations (you need `Mode.FailFast[F]` in scope in this case)

```scala
given Mode.FailFast.Either[String] with {}

bad.fallibleTo[Person]
// res46: Either[String, Person] = Left(value = "Name should not be blank!")
good.fallibleTo[Person]
// res47: Either[String, Person] = Right(
//   value = Person(name = "ValidName", age = 24, socialSecurityNo = "SOCIALNO")
// )
```

<details>
  <summary>Click to see the generated code</summary>

``` scala 
  ((given_Either_String.flatMap[Type, Person](
    Name.failFastWrappingTransformer.transform(bad.name),
    (name: Type) =>
      given_Either_String.flatMap[Type, Person](
        Age.failFastWrappingTransformer.transform(bad.age),
        (age: Type) =>
          given_Either_String.map[Type, Person](
            SSN.failFastWrappingTransformer.transform(bad.socialSecurityNo),
            (socialSecurityNo: Type) => new Person(name = name, age = age, socialSecurityNo = socialSecurityNo)
          )
      )
  ): Either[String, Person]): Either[String, Person])
```
</details>

### Configuring fallible transformations

If we were to dissect how the types behind config options are structured, we'd see this:

```scala
opaque type Field[A, B] <: Field.Fallible[Nothing, A, B] = Field.Fallible[Nothing, A, B]

object Field {
  opaque type Fallible[+F[+x], A, B] = Unit
}
```

Non-fallible config options are a subtype of fallible configs, i.e. all the things mentioned in [`Configuring transformations`](#configuring-transformations) are also applicable to fallible configurations.

#### Fallible product configurations

* `Field.fallibleConst` - a fallible variant of `Field.const` that allows for supplying values wrapped in an `F`

```scala
given Mode.Accumulating.Either[String, List] with {}

bad
  .into[Person]
  .fallible
  .transform(
    Field.fallibleConst(_.name, Name.makeAccumulating("ConstValidName")),
    Field.fallibleConst(_.age, Age.makeAccumulating(25))
  )
// res49: Either[List[String], Person] = Right(
//   value = Person(
//     name = "ConstValidName",
//     age = 25,
//     socialSecurityNo = "SOCIALNO"
//   )
// )
```

<details>
  <summary>Click to see the generated code</summary>

``` scala 
  {
    val Fallible_this
      : Fallible[[A >: Nothing <: Any] =>> Either[List[String], A], given_Either_String_List, UnvalidatedPerson, Person] =
      into[UnvalidatedPerson](bad)[Person]
        .fallible[[A >: Nothing <: Any] =>> Either[List[String], A], given_Either_String_List.type](given_Either_String_List)

    {
      val source$proxy2: UnvalidatedPerson = Fallible_this.inline$source
      val F$proxy2: given_Either_String_List.type = Fallible_this.inline$F

      F$proxy2.map[Tuple2[Type, Tuple2[Type, Type]], Person](
        F$proxy2.product[Type, Tuple2[Type, Type]](
          Name.makeAccumulating("ConstValidName"),
          F$proxy2.product[Type, Type](
            Age.makeAccumulating(25),
            SSN.accumulatingWrappingTransformer.transform(source$proxy2.socialSecurityNo)
          )
        ),
        (value: Tuple2[Type, Tuple2[Type, Type]]) =>
          new Person(name = value._1, age = value._2._1, socialSecurityNo = value._2._2)
      ): Either[List[String], Person]
    }: Either[List[String], Person]
  }
```
</details>

* `Field.fallibleComputed` - a fallible variant of `Field.computed` that allows for supplying functions that return values wrapped in an `F`

```scala
given Mode.Accumulating.Either[String, List] with {}

bad
  .into[Person]
  .fallible
  .transform(
    Field.fallibleComputed(_.name, uvp => Name.makeAccumulating(uvp.name + "ConstValidName")),
    Field.fallibleComputed(_.age, uvp => Age.makeAccumulating(uvp.age + 25))
  )
// res51: Either[List[String], Person] = Right(
//   value = Person(
//     name = "ConstValidName",
//     age = 24,
//     socialSecurityNo = "SOCIALNO"
//   )
// )
```

<details>
  <summary>Click to see the generated code</summary>

``` scala 
  {
    val Fallible_this
      : Fallible[[A >: Nothing <: Any] =>> Either[List[String], A], given_Either_String_List, UnvalidatedPerson, Person] =
      into[UnvalidatedPerson](bad)[Person]
        .fallible[[A >: Nothing <: Any] =>> Either[List[String], A], given_Either_String_List.type](given_Either_String_List)

    {
      val source$proxy4: UnvalidatedPerson = Fallible_this.inline$source
      val F$proxy4: given_Either_String_List.type = Fallible_this.inline$F

      F$proxy4.map[Tuple2[Type, Tuple2[Type, Type]], Person](
        F$proxy4.product[Type, Tuple2[Type, Type]](
          Name.makeAccumulating(source$proxy4.name.+("ConstValidName")),
          F$proxy4.product[Type, Type](
            Age.makeAccumulating(source$proxy4.age.+(25)),
            SSN.accumulatingWrappingTransformer.transform(source$proxy4.socialSecurityNo)
          )
        ),
        (value: Tuple2[Type, Tuple2[Type, Type]]) =>
          new Person(name = value._1, age = value._2._1, socialSecurityNo = value._2._2)
      ): Either[List[String], Person]
    }: Either[List[String], Person]
  }
```
</details>

#### Fallible coproduct configurations

Let's define a wire enum (pretend that it's coming from... somewhere) and a domain enum that doesn't exactly align with the wire one.
```scala
object wire {
  enum ReleaseKind {
    case LP, EP, Single
  }
}

object domain {
  enum ReleaseKind {
    case EP, LP
  }
}
```

* `Case.fallibleConst` - a fallible variant of `Case.const` that allows for supplying values wrapped in an `F`

```scala
given Mode.FailFast.Either[String] with {}

wire.ReleaseKind.Single
  .into[domain.ReleaseKind]
  .fallible
  .transform(
    Case.fallibleConst(_.at[wire.ReleaseKind.Single.type], Left("Unsupported release kind"))
  )
// res53: Either[String, ReleaseKind] = Left(
//   value = "Unsupported release kind"
// )
```
<details>
  <summary>Click to see the generated code</summary>

``` scala 
  {
    val Fallible_this: Fallible[[A >: Nothing <: Any] =>> Either[String, A], given_Either_String, ReleaseKind, ReleaseKind] =
      into[ReleaseKind](wire.ReleaseKind.Single)[domain.ReleaseKind]
        .fallible[[A >: Nothing <: Any] =>> Either[String, A], given_Either_String.type](given_Either_String)

    {
      val source$proxy6: ReleaseKind = Fallible_this.inline$source
      val F$proxy6: given_Either_String.type = Fallible_this.inline$F

      if (source$proxy6.isInstanceOf[LP.type]) F$proxy6.pure[LP.type](domain.ReleaseKind.LP)
      else if (source$proxy6.isInstanceOf[EP.type]) F$proxy6.pure[EP.type](domain.ReleaseKind.EP)
      else if (source$proxy6.isInstanceOf[Single.type]) Left.apply[String, Nothing]("Unsupported release kind")
      else throw new RuntimeException("Unhandled case. This is most likely a bug in ducktape."): Either[String, ReleaseKind]
    }: Either[String, ReleaseKind]
  }
```
</details>

* `Case.fallibleComputed` - a fallible variant of `Case.computed` that allows for supplying functions that return values wrapped in an `F`

```scala
given Mode.FailFast.Either[String] with {}

// Type inference is tricky with this one. The function being passed in needs to be typed with the exact expected type.
def handleSingle(value: wire.ReleaseKind): Either[String, domain.ReleaseKind] = 
  Left("It's a single alright, too bad we don't support it")

wire.ReleaseKind.Single
  .into[domain.ReleaseKind]
  .fallible
  .transform(
    Case.fallibleComputed(_.at[wire.ReleaseKind.Single.type], handleSingle)
  )
// res55: Either[String, ReleaseKind] = Left(
//   value = "It's a single alright, too bad we don't support it"
// )
```

<details>
  <summary>Click to see the generated code</summary>

``` scala 
  {
    val Fallible_this: Fallible[[A >: Nothing <: Any] =>> Either[String, A], given_Either_String, ReleaseKind, ReleaseKind] =
      into[ReleaseKind](wire.ReleaseKind.Single)[domain.ReleaseKind]
        .fallible[[A >: Nothing <: Any] =>> Either[String, A], given_Either_String.type](given_Either_String)

    {
      val source$proxy8: ReleaseKind = Fallible_this.inline$source
      val F$proxy8: given_Either_String.type = Fallible_this.inline$F

      if (source$proxy8.isInstanceOf[LP.type]) F$proxy8.pure[LP.type](domain.ReleaseKind.LP)
      else if (source$proxy8.isInstanceOf[EP.type]) F$proxy8.pure[EP.type](domain.ReleaseKind.EP)
      else if (source$proxy8.isInstanceOf[Single.type]) {
        val value: ReleaseKind = source$proxy8.asInstanceOf[Single.type]
        handleSingle(value)
      } else throw new RuntimeException("Unhandled case. This is most likely a bug in ducktape."): Either[String, ReleaseKind]
    }: Either[String, ReleaseKind]
  }
```
</details>

### Building custom instances of fallible transformers
Life is not always lolipops and crisps and sometimes you need to write a typeclass instance by hand. Worry not though, just like in the case of total transformers, we can easily define custom instances with the help of the configuration DSL (which, let's write it down once again, is a superset of total transformers' DSL).

By all means go wild with the configuration options, I'm too lazy to write them all out here again.
```scala
given Mode.Accumulating.Either[String, List] with {}

val customAccumulating =
  Transformer
    .define[UnvalidatedPerson, Person]
    .fallible
    .build(
      Field.fallibleConst(_.name, Name.makeAccumulating("IAmAlwaysValidNow!"))
    )
```
<details>
  <summary>Click to see the generated code</summary>

``` scala 
  {
    val Fallible_this
      : Fallible[[A >: Nothing <: Any] =>> Either[List[String], A], given_Either_String_List, UnvalidatedPerson, Person] =
      Transformer
        .define[UnvalidatedPerson, Person]
        .fallible[[A >: Nothing <: Any] =>> Either[List[String], A], given_Either_String_List.type](given_Either_String_List)

    new FromFunction[[A >: Nothing <: Any] =>> Either[List[String], A], UnvalidatedPerson, Person](
      (source: UnvalidatedPerson) => {
        val F$proxy10: given_Either_String_List.type = Fallible_this.inline$F

        F$proxy10.map[Tuple2[Type, Tuple2[Type, Type]], Person](
          F$proxy10.product[Type, Tuple2[Type, Type]](
            Name.makeAccumulating("IAmAlwaysValidNow!"),
            F$proxy10.product[Type, Type](
              Age.accumulatingWrappingTransformer.transform(source.age),
              SSN.accumulatingWrappingTransformer.transform(source.socialSecurityNo)
            )
          ),
          (value: Tuple2[Type, Tuple2[Type, Type]]) =>
            new Person(name = value._1, age = value._2._1, socialSecurityNo = value._2._2)
        ): Either[List[String], Person]
      }
    ): Fallible[[A >: Nothing <: Any] =>> Either[List[String], A], UnvalidatedPerson, Person]
  }
```
</details>

```scala
given Mode.FailFast.Either[String] with {}

val customFailFast =
  Transformer
    .define[UnvalidatedPerson, Person]
    .fallible
    .build(
      Field.fallibleComputed(_.age, uvp => Age.make(uvp.age + 30))
    )
```

<details>
  <summary>Click to see the generated code</summary>

``` scala 
  {
    val Fallible_this: Fallible[[A >: Nothing <: Any] =>> Either[String, A], given_Either_String, UnvalidatedPerson, Person] =
      Transformer
        .define[UnvalidatedPerson, Person]
        .fallible[[A >: Nothing <: Any] =>> Either[String, A], given_Either_String.type](given_Either_String)

    new FromFunction[[A >: Nothing <: Any] =>> Either[String, A], UnvalidatedPerson, Person]((source: UnvalidatedPerson) => {
      val F$proxy12: given_Either_String.type = Fallible_this.inline$F

      F$proxy12.flatMap[Type, Person](
        Name.failFastWrappingTransformer.transform(source.name),
        (name: Type) =>
          F$proxy12.flatMap[Type, Person](
            Age.make(source.age.+(30)),
            (age: Type) =>
              F$proxy12.map[Type, Person](
                SSN.failFastWrappingTransformer.transform(source.socialSecurityNo),
                (socialSecurityNo: Type) => new Person(name = name, age = age, socialSecurityNo = socialSecurityNo)
              )
          )
      ): Either[String, Person]
    }): Fallible[[A >: Nothing <: Any] =>> Either[String, A], UnvalidatedPerson, Person]
  }
```
</details>

And for the ones that are not keen on writing out method arguments:
```scala
given Mode.Accumulating.Either[String, List] with {}

val customAccumulatingVia =
  Transformer
    .defineVia[UnvalidatedPerson](Person.apply)
    .fallible
    .build(
      Field.fallibleConst(_.name, Name.makeAccumulating("IAmAlwaysValidNow!"))
    )
```

<details>
  <summary>Click to see the generated code</summary>

``` scala 
  {
  val $proxy2: DefinitionViaBuilder {
    type PartiallyApplied >: [Source >: Nothing <: Any] => Unit <: [Source >: Nothing <: Any] => Unit
  } = DefinitionViaBuilder.$asInstanceOf$[DefinitionViaBuilder {
    type PartiallyApplied >: [Source >: Nothing <: Any] => Unit <: [Source >: Nothing <: Any] => Unit
  }]
  val DefinitionViaBuilder$_this: $proxy2.type = $proxy2
  val partial$proxy2: PartiallyApplied[UnvalidatedPerson] & PartiallyApplied[UnvalidatedPerson] = Transformer.defineVia[UnvalidatedPerson].$asInstanceOf$[PartiallyApplied[UnvalidatedPerson] & PartiallyApplied[UnvalidatedPerson]]
  val builder: DefinitionViaBuilder[UnvalidatedPerson, Nothing, Function3[Name, Age, SSN, Person], Nothing] = DefinitionViaBuilder$_this.inline$instance[UnvalidatedPerson, Function3[Name, Age, SSN, Person]](((name: Name, age: Age, socialSecurityNo: SSN) => Person.apply(name, age, socialSecurityNo)))
  val $proxy5: DefinitionViaBuilder {
    type PartiallyApplied >: [Source >: Nothing <: Any] => Unit <: [Source >: Nothing <: Any] => Unit
  } = DefinitionViaBuilder.$asInstanceOf$[DefinitionViaBuilder {
    type PartiallyApplied >: [Source >: Nothing <: Any] => Unit <: [Source >: Nothing <: Any] => Unit
  }]
  val Fallible_this: Fallible[[A >: Nothing <: Any] =>> Either[List[String], A], given_Either_String_List, UnvalidatedPerson, Person, Function3[Name, Age, SSN, Person], FunctionArguments {
    val name: Name
    val age: Age
    val socialSecurityNo: SSN
  }] = builder.asInstanceOf[[args >: Nothing <: FunctionArguments, retTpe >: Nothing <: Any] =>> DefinitionViaBuilder[UnvalidatedPerson, retTpe, Function3[Name, Age, SSN, Person], args][FunctionArguments {
    val name: Name
    val age: Age
    val socialSecurityNo: SSN
  }, Person]].fallible[[A >: Nothing <: Any] =>> Either[List[String], A], given_Either_String_List.type](given_Either_String_List)

  (new FromFunction[[A >: Nothing <: Any] =>> Either[List[String], A], UnvalidatedPerson, Person](((value: UnvalidatedPerson) => {
    val function$proxy22: Function3[Name, Age, SSN, Person] = Fallible_this.inline$function
    val F$proxy14: given_Either_String_List.type = Fallible_this.inline$F

    (F$proxy14.map[Tuple2[Type, Tuple2[Type, Type]], Person](F$proxy14.product[Type, Tuple2[Type, Type]](Name.makeAccumulating("IAmAlwaysValidNow!"), F$proxy14.product[Type, Type](Age.accumulatingWrappingTransformer.transform(value.age), SSN.accumulatingWrappingTransformer.transform(value.socialSecurityNo))), ((`value₂`: Tuple2[Type, Tuple2[Type, Type]]) => function$proxy22.apply(`value₂`._1, `value₂`._2._1, `value₂`._2._2))): Either[List[String], Person])
  })): Fallible[[A >: Nothing <: Any] =>> Either[List[String], A], UnvalidatedPerson, Person])
}
```
</details>

```scala
given Mode.FailFast.Either[String] with {}

val customFailFastVia =
  Transformer
    .defineVia[UnvalidatedPerson](Person.apply)
    .fallible
    .build(
      Field.fallibleComputed(_.age, uvp => Age.make(uvp.age + 30))
    )
```

## Transfomation rules

Let's go over the priority and rules that `ducktape` uses to create a transformation (in the same order they're tried in the implementation):

1. User supplied `Transformers`

Custom instances of a `Transfomer` are always prioritized since these also function as an extension mechanism of the library.

```scala
given Transformer[String, List[String]] = str => str :: Nil

"single value".to[List[String]]
// res60: List[String] = List("single value")
```

<details>
  <summary>Click to see the generated code</summary>

``` scala 
  ((given_Transformer_String_List.transform("single value"): List[String]): List[String])
```
</details>

2. Upcasting

Transforming a type to its supertype is just an upcast.

```scala
// (Int | String) >: Int
1.to[Int | String]
// res62: Int | String = 1
```
<details>
  <summary>Click to see the generated code</summary>

``` scala 
  ((1: Int | String): Int | String)
```
</details>

3. Mapping over an `Option`

Transforming between options comes down to mapping over it and recursively deriving a transformation for the value inside.

```scala
given Transformer[Int, String] = int => int.toString

Option(1).to[Option[String]]
// res64: Option[String] = Some(value = "1")
```

<details>
  <summary>Click to see the generated code</summary>

``` scala 
  {
    val source$proxy10: Option[Int] = Option.apply[Int](1)

    (source$proxy10.map[String]((src: Int) => given_Transformer_Int_String.transform(src)): Option[String]): Option[String]
  }
```
</details>

4. Transforming and wrapping in an `Option`

If a transformation between two types is possible then transforming between the source type and an `Option` of the destination type is just wrapping the transformation result in a `Some`.

```scala
1.to[Option[Int | String]]
// res66: Option[Int | String] = Some(value = 1)
```

<details>
  <summary>Click to see the generated code</summary>

``` scala 
  ((Some.apply[Int | String](1): Option[Int | String]): Option[Int | String])
```
</details>

5. Mapping over and changing the collection type

```scala
//`.to` is already a method on collections
import io.github.arainko.ducktape.to as convertTo

List(1, 2, 3, 4).convertTo[Vector[Int | String]]
// res68: Vector[Int | String] = Vector(1, 2, 3, 4)
```

<details>
  <summary>Click to see the generated code</summary>

``` scala 
  {
    val source$proxy12: List[Int] = List.apply[Int](1, 2, 3, 4)

    (source$proxy12
      .map[Int | String]((src: Int) => src)
      .to[Vector[Int | String]](iterableFactory[Int | String]): Vector[Int | String]): Vector[Int | String]
  }
```
</details>

6. Transforming between case classes

A source case class can be transformed into the destination case class given that:
* source has fields whose names cover all of the destination's fields,
* a transformation for the types corresponding to those fields can be derived.

```scala
import io.github.arainko.ducktape.*

case class SourceToplevel(level1: SourceLevel1)
case class SourceLevel1(extra: String, int: Int, level2s: List[SourceLevel2])
case class SourceLevel2(value: Int)

case class DestToplevel(level1: DestLevel1)
case class DestLevel1(int: Int | String, level2s: Vector[DestLevel2])
case class DestLevel2(value: Option[Int])

SourceToplevel(SourceLevel1("extra", 1, List(SourceLevel2(1), SourceLevel2(2)))).to[DestToplevel]
// res71: DestToplevel = DestToplevel(
//   level1 = DestLevel1(
//     int = 1,
//     level2s = Vector(
//       DestLevel2(value = Some(value = 1)),
//       DestLevel2(value = Some(value = 2))
//     )
//   )
// )
```
<details>
  <summary>Click to see the generated code</summary>

``` scala 
  {
    val source$proxy14: SourceToplevel =
      SourceToplevel.apply(SourceLevel1.apply("extra", 1, List.apply[SourceLevel2](SourceLevel2.apply(1), SourceLevel2.apply(2))))

    (new DestToplevel(level1 =
      new DestLevel1(
        int = source$proxy14.level1.int,
        level2s = source$proxy14.level1.level2s
          .map[DestLevel2]((src: SourceLevel2) => new DestLevel2(value = Some.apply[Int](src.value)))
          .to[Vector[DestLevel2]](iterableFactory[DestLevel2])
      )
    ): DestToplevel): DestToplevel
  }
```
</details>

7. Transforming between enums/sealed traits

A source coproduct can be transformed into the destination coproduct given that:
* destination's children have names that match all of the source's children,
* a transformation between those two corresponding types can be derived.

```scala
sealed trait PaymentMethod

object PaymentMethod {
  case class Card(name: String, digits: Long, expires: Long) extends PaymentMethod
  case object Cash extends PaymentMethod
  case class PayPal(email: String) extends PaymentMethod
}

enum OtherPaymentMethod {
  case Card(name: String, digits: Long, expires: Long)
  case PayPal(email: String)
  case Cash
  case FakeMoney
}

(PaymentMethod.Cash: PaymentMethod).to[OtherPaymentMethod]
// res73: OtherPaymentMethod = Cash
```

<details>
  <summary>Click to see the generated code</summary>

``` scala 
  {
    val source$proxy16: PaymentMethod = PaymentMethod.Cash: PaymentMethod

    (if (source$proxy16.isInstanceOf[Card])
       new Card(
         name = source$proxy16.asInstanceOf[Card].name,
         digits = source$proxy16.asInstanceOf[Card].digits,
         expires = source$proxy16.asInstanceOf[Card].expires
       )
     else if (source$proxy16.isInstanceOf[Cash.type]) Cash
     else if (source$proxy16.isInstanceOf[PayPal]) new PayPal(email = source$proxy16.asInstanceOf[PayPal].email)
     else
       throw new RuntimeException(
         "Unhandled case. This is most likely a bug in ducktape."
       ): OtherPaymentMethod): OtherPaymentMethod
  }
```
</details>

8. Same named singletons

Transformations between same named singletons come down to just reffering to the destination singleton.

```scala
object example1 {
  case object Singleton
}

object example2 {
  case object Singleton
}

example1.Singleton.to[example2.Singleton.type]
// res75: Singleton = Singleton
```
<details>
  <summary>Click to see the generated code</summary>

``` scala 
  Singleton
```
</details>

9. Unwrapping a value class

```scala
case class Wrapper1(value: Int) extends AnyVal

Wrapper1(1).to[Int]
// res77: Int = 1
```

<details>
  <summary>Click to see the generated code</summary>

``` scala 
  {
    val source$proxy18: Wrapper1 = Wrapper1.apply(1)

    (source$proxy18.value: Int): Int
  }
```
</details>

10. Wrapping a value class

```scala
case class Wrapper2(value: Int) extends AnyVal

1.to[Wrapper2]
// res79: Wrapper2 = Wrapper2(value = 1)
```

<details>
  <summary>Click to see the generated code</summary>

``` scala 
  ((new Wrapper2(1): Wrapper2): Wrapper2)
```
</details>

11. Automatically derived `Transformer.Derived`

Instances of `Transformer.Derived` are automatically derived as a fallback to support use cases where a generic type (eg. a field of a case class) is unknown at definition site.

Note that `Transformer[A, B] <: Transformer.Derived[A, B]` so any `Transformer` in scope is eligible to become a `Transformer.Derived`.

```scala
final case class Source[A](field1: Int, field2: String, generic: A)
final case class Dest[A](field1: Int, field2: String, generic: A)

def transformSource[A, B](source: Source[A])(using Transformer.Derived[A, B]): Dest[B] = source.to[Dest[B]]

transformSource[Int, Option[Int]](Source(1, "2", 3))
// res81: Dest[Option[Int]] = Dest(
//   field1 = 1,
//   field2 = "2",
//   generic = Some(value = 3)
// )
```

<details>
  <summary>Click to see the generated code</summary>

``` scala 
  {
    def transformSource[A, B](source: Source[A])(using x$2: Transformer.Derived[A, B]): Dest[B] =
      (new Dest[B](field1 = source.field1, field2 = source.field2, generic = x$2.transform(source.generic)): Dest[B]): Dest[B]
    transformSource[Int, Option[Int]](Source.apply[Int](1, "2", 3))(
      new FromFunction[Int, Option[Int]]((value: Int) => Some.apply[Int](value): Option[Int]): Derived[Int, Option[Int]]
    )
  }
```
</details>

## Coming from 0.1.x

While `ducktape 0.2.x` is not binary-compatible with `ducktape 0.1.x` it tries to be as source-compatible as possible with a few caveats (the following is a non-exhaustive list of source-incompatible changes that have a chance to be visible by the end users):

* instances of `Transformers` and `Transformer.Fallible` are NOT auto-deriveable anymore. Any code that relies on auto derivation of these should switch to `Transformer.Derived` and `Transformer.Fallible.Derived`,
* given definitions inside the companion of `Transformer` and `Transformer.Fallible` (like `Transformer.betweenNonOptionOption` etc) are gone and should be replaced with calls to `Transformer.derive` and `Transformer.Fallible.derive` with appropriate types as the type arguments,
* the signature of `Mode[F]#traverseCollection` has changed from 
```scala
 def traverseCollection[A, B, AColl[x] <: Iterable[x], BColl[x] <: Iterable[x]](collection: AColl[A])(using
    transformer: FallibleTransformer[F, A, B],
    factory: Factory[B, BColl[B]]
  ): F[BColl[B]]
```
to
```scala
def traverseCollection[A, B, AColl <: Iterable[A], BColl <: Iterable[B]](
    collection: AColl,
    transformation: A => F[B]
  )(using factory: Factory[B, BColl]): F[BColl]
```
* `BuilderConfig[A, B]` is replaced by the union of `Field[A, B]` and `Case[A, B]`, while `ArgBuilderConfig[A, B]` is replaced with `Field[A, B]`,
* `FunctionMirror` is gone with no replacement (it was pretty much a leaking impl detail).
