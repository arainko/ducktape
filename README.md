# ![ducktape-logo-32](https://user-images.githubusercontent.com/46346508/236060869-3b118075-f660-44c9-9d0d-d40fba5c8db0.svg) ducktape 0.2.x

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.github.arainko/ducktape_3/badge.svg?style=flat-square)](https://maven-badges.herokuapp.com/maven-central/io.github.arainko/ducktape_3)

*ducktape* is a library for boilerplate-less and configurable transformations between case classes and enums/sealed traits for Scala 3. Directly inspired by [chimney](https://github.com/scalalandio/chimney).

If this project interests you, please drop a 🌟 - these things are worthless but give me a dopamine rush nonetheless.

## Installation
```scala
libraryDependencies += "io.github.arainko" %% "ducktape" % "0.2.0-M2"

// or if you're using Scala.js or Scala Native
libraryDependencies += "io.github.arainko" %%% "ducktape" % "0.2.0-M2"
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
//       expires = 2023-12-18T15:00:25.065351Z
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
        else throw new RuntimeException("Unhandled condition encountered during Coproduct Transformer derivation")
      )
      .to[Vector[Payment]](iterableFactory[Payment])
    val status$2: Some[Status] = Some.apply[Status](
      if (MdocApp.this.wirePerson.status.isInstanceOf[Registered.type]) MdocApp.this.domain.Status.Registered
      else if (MdocApp.this.wirePerson.status.isInstanceOf[PendingRegistration.type])
        MdocApp.this.domain.Status.PendingRegistration
      else if (MdocApp.this.wirePerson.status.isInstanceOf[Removed.type]) MdocApp.this.domain.Status.Removed
      else throw new RuntimeException("Unhandled condition encountered during Coproduct Transformer derivation")
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
//   case Cash
//       ^
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
//       expires = 2023-12-18T15:00:25.078582Z
//     )
//   ),
//   updatedAt = Some(value = 1970-01-01T00:00:00Z)
// )
```

<details>
  <summary>Click to see the generated code</summary>
  
``` scala 
  {
    val AppliedBuilder_this: AppliedBuilder[Person, Person] = into[Person](MdocApp1.this.wirePerson1)[MdocApp1.this.domain.Person]

    {
      val value$proxy3: Person = AppliedBuilder_this.inline$value

      {
        val paymentMethods$4: Vector[Payment] = value$proxy3.paymentMethods
          .map[Payment]((src: PaymentMethod) =>
            if (src.isInstanceOf[Card])
              new Card(name = "CONST NAME", digits = src.asInstanceOf[Card].digits, expires = src.asInstanceOf[Card].expires)
            else if (src.isInstanceOf[PayPal]) new PayPal(email = src.asInstanceOf[PayPal].email)
            else if (src.isInstanceOf[Cash.type]) MdocApp1.this.domain.Payment.Cash
            else throw new RuntimeException("Unhandled condition encountered during Coproduct Transformer derivation")
          )
          .to[Vector[Payment]](iterableFactory[Payment])
        val status$4: Some[Status] = Some.apply[Status](
          if (value$proxy3.status.isInstanceOf[Registered.type]) MdocApp1.this.domain.Status.Registered
          else if (value$proxy3.status.isInstanceOf[PendingRegistration.type]) MdocApp1.this.domain.Status.PendingRegistration
          else if (value$proxy3.status.isInstanceOf[Removed.type]) MdocApp1.this.domain.Status.Removed
          else throw new RuntimeException("Unhandled condition encountered during Coproduct Transformer derivation")
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
// res5: Person = Person(
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
    firstName = MdocApp4.this.wirePerson.firstName,
    lastName = MdocApp4.this.wirePerson.lastName,
    paymentMethods = MdocApp4.this.wirePerson.paymentMethods
      .map[PaymentMethod]((src: PaymentMethod) =>
        if (src.isInstanceOf[Card]) new Card(name = src.asInstanceOf[Card].name, digits = src.asInstanceOf[Card].digits)
        else if (src.isInstanceOf[PayPal]) new PayPal(email = src.asInstanceOf[PayPal].email)
        else if (src.isInstanceOf[Cash.type]) MdocApp4.this.domain.PaymentMethod.Cash
        else throw new RuntimeException("Unhandled condition encountered during Coproduct Transformer derivation")
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
// res7: Person = Person(
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
    val AppliedBuilder_this: AppliedBuilder[Person, Person] = into[Person](MdocApp4.this.wirePerson)[MdocApp4.this.domain.Person]

    {
      val value$proxy7: Person = AppliedBuilder_this.inline$value

      new Person(
        firstName = value$proxy7.firstName,
        lastName = value$proxy7.lastName,
        paymentMethods = value$proxy7.paymentMethods
          .map[PaymentMethod]((src: PaymentMethod) =>
            if (src.isInstanceOf[Card]) new Card(name = src.asInstanceOf[Card].name, digits = src.asInstanceOf[Card].digits)
            else if (src.isInstanceOf[PayPal]) new PayPal(email = "overridden@email.com")
            else if (src.isInstanceOf[Cash.type]) MdocApp4.this.domain.PaymentMethod.Cash
            else throw new RuntimeException("Unhandled condition encountered during Coproduct Transformer derivation")
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
// res9: Person = Person(
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
    val firstName: String = MdocApp4.this.wirePerson.firstName
    val lastName: String = MdocApp4.this.wirePerson.lastName
    val paymentMethods: Vector[PaymentMethod] = MdocApp4.this.wirePerson.paymentMethods
      .map[PaymentMethod]((src: PaymentMethod) =>
        if (src.isInstanceOf[Card]) new Card(name = src.asInstanceOf[Card].name, digits = src.asInstanceOf[Card].digits)
        else if (src.isInstanceOf[PayPal]) new PayPal(email = src.asInstanceOf[PayPal].email)
        else if (src.isInstanceOf[Cash.type]) MdocApp4.this.domain.PaymentMethod.Cash
        else throw new RuntimeException("Unhandled condition encountered during Coproduct Transformer derivation")
      )
      .to[Vector[PaymentMethod]](iterableFactory[PaymentMethod])
    MdocApp4.this.domain.Person.apply(firstName, lastName, paymentMethods)
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
// res11: Person = Person(
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
        MdocApp4.this.wirePerson,
        (firstName: String, lastName: String, paymentMethods: Vector[PaymentMethod]) =>
          MdocApp4.this.domain.Person.apply(firstName, lastName, paymentMethods)
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
      val function$proxy12: Function3[String, String, Vector[PaymentMethod], Person] = AppliedViaBuilder_this.inline$function

      function$proxy12.apply(
        value$proxy11.firstName,
        value$proxy11.lastName,
        value$proxy11.paymentMethods
          .map[PaymentMethod]((src: PaymentMethod) =>
            if (src.isInstanceOf[Card]) new Card(name = src.asInstanceOf[Card].name, digits = src.asInstanceOf[Card].digits)
            else if (src.isInstanceOf[PayPal]) new PayPal(email = "overridden@email.com")
            else if (src.isInstanceOf[Cash.type]) MdocApp4.this.domain.PaymentMethod.Cash
            else throw new RuntimeException("Unhandled condition encountered during Coproduct Transformer derivation")
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
//     def transformSource[A, B](source: Source[A])(using Transformer.Derived[A, B]): Dest[B]  =  source.to[Dest[B]]
//                                                                                           ^
```

The newly added field (`age`) and enum case (`PaymentMethod.Transfer`) do not have a corresponding mapping, let's say we want to set the age field to a constant value of 24 and when a PaymentMethod.Transfer is encountered we map it to `Cash` instead.

```scala
wirePerson
  .into[domain.Person]
  .transform(
    Field.const(_.age, 24),
    Case.const(_.paymentMethods.element.at[wire.PaymentMethod.Transfer], domain.PaymentMethod.Cash)
  )
// res15: Person = Person(
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
            else throw new RuntimeException("Unhandled condition encountered during Coproduct Transformer derivation")
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
// card: Card = Card(name = "J. Doe", digits = 213712345L)
```

* `Field.const` - allows to supply a constant value for a given field
```scala
card
  .into[PaymentBand]
  .transform(Field.const(_.color, "blue"))
// res17: PaymentBand = PaymentBand(
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
// res19: PaymentBand = PaymentBand(
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
// res21: PaymentBand = PaymentBand(
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
// source: FieldSource = FieldSource(
//   color = "magenta",
//   digits = 123445678L,
//   extra = 23
// )

card
  .into[PaymentBand]
  .transform(Field.allMatching(paymentBand => paymentBand, source))
// res23: PaymentBand = PaymentBand(
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
// res25: PaymentMethod = Cash
```
<details>
  <summary>Click to see the generated code</summary>

``` scala 
  {
    val AppliedBuilder_this: AppliedBuilder[PaymentMethod, PaymentMethod] = into[PaymentMethod](transfer)[domain.PaymentMethod]

    {
      val value$proxy29: PaymentMethod = AppliedBuilder_this.inline$value

      if (value$proxy29.isInstanceOf[Card])
        new Card(name = value$proxy29.asInstanceOf[Card].name, digits = value$proxy29.asInstanceOf[Card].digits)
      else if (value$proxy29.isInstanceOf[PayPal]) new PayPal(email = value$proxy29.asInstanceOf[PayPal].email)
      else if (value$proxy29.isInstanceOf[Cash.type]) Cash
      else if (value$proxy29.isInstanceOf[Transfer]) domain.PaymentMethod.Cash
      else throw new RuntimeException("Unhandled condition encountered during Coproduct Transformer derivation"): PaymentMethod
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
// res27: PaymentMethod = Card(name = "J. Doe", digits = 2764262L)
```

<details>
  <summary>Click to see the generated code</summary>

``` scala 
  {
    val AppliedBuilder_this: AppliedBuilder[PaymentMethod, PaymentMethod] = into[PaymentMethod](transfer)[domain.PaymentMethod]

    {
      val value$proxy32: PaymentMethod = AppliedBuilder_this.inline$value

      if (value$proxy32.isInstanceOf[Card])
        new Card(name = value$proxy32.asInstanceOf[Card].name, digits = value$proxy32.asInstanceOf[Card].digits)
      else if (value$proxy32.isInstanceOf[PayPal]) new PayPal(email = value$proxy32.asInstanceOf[PayPal].email)
      else if (value$proxy32.isInstanceOf[Cash.type]) Cash
      else if (value$proxy32.isInstanceOf[Transfer]) {
        val transfer: Transfer = value$proxy32.asInstanceOf[Transfer]

        domain.PaymentMethod.Card.apply("J. Doe", augmentString(transfer.accountNo).toLong): PaymentMethod
      } else throw new RuntimeException("Unhandled condition encountered during Coproduct Transformer derivation"): PaymentMethod
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
// res29: Person = Person(
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
// res30: Person = Person(
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
// res31: Person = Person(
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
//     Field.const(_.paymentMethods.element, domain.PaymentMethod.Cash), // <-- then the field below it
//     ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
```

## Transfomation rules

Let's go over the priority and rules that `ducktape` uses to create a transformation (in the same order they're tried in the implementation):

#### 1. User supplied `Transformers`

Custom instances of a `Transfomer` are always prioritized since these also function as an extension mechanism of the library.

```scala
given Transformer[String, List[String]] = str => str :: Nil

"single value".to[List[String]]
// res33: List[String] = List("single value")
```

<details>
  <summary>Click to see the generated code</summary>

``` scala 
  ((given_Transformer_String_List.transform("single value"): List[String]): List[String])
```
</details>

#### 2. Upcasting

Transforming a type to its supertype is just an upcast.

```scala
// (Int | String) >: Int
1.to[Int | String]
// res35: Int | String = 1
```
<details>
  <summary>Click to see the generated code</summary>

``` scala 
  ((1: Int | String): Int | String)
```
</details>

#### 3. Mapping over an `Option`

Transforming between options comes down to mapping over it and recursively deriving a transformation for the value inside.

```scala
given Transformer[Int, String] = int => int.toString

Option(1).to[Option[String]]
// res37: Option[String] = Some(value = "1")
```

<details>
  <summary>Click to see the generated code</summary>

``` scala 
  {
    val source$proxy2: Option[Int] = Option.apply[Int](1)

    (source$proxy2.map[String]((src: Int) => given_Transformer_Int_String.transform(src)): Option[String]): Option[String]
  }
```
</details>

#### 4. Transforming and wrapping in an `Option`

If a transformation between two types is possible then transforming between the source type and an `Option` of the destination type is just wrapping the transformation result in a `Some`.

```scala
1.to[Option[Int | String]]
// res39: Option[Int | String] = Some(value = 1)
```

<details>
  <summary>Click to see the generated code</summary>

``` scala 
  ((Some.apply[Int | String](1): Option[Int | String]): Option[Int | String])
```
</details>

#### 5. Mapping over and changing the collection type

```scala
//`.to` is already a method on collections
import io.github.arainko.ducktape.to as convertTo

List(1, 2, 3, 4).convertTo[Vector[Int | String]]
// res41: Vector[Int | String] = Vector(1, 2, 3, 4)
```

<details>
  <summary>Click to see the generated code</summary>

``` scala 
  {
    val source$proxy4: List[Int] = List.apply[Int](1, 2, 3, 4)

    (source$proxy4
      .map[Int | String]((src: Int) => src)
      .to[Vector[Int | String]](iterableFactory[Int | String]): Vector[Int | String]): Vector[Int | String]
  }
```
</details>

#### 6. Transforming between case classes

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
// res44: DestToplevel = DestToplevel(
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
    val source$proxy6: SourceToplevel =
      SourceToplevel.apply(SourceLevel1.apply("extra", 1, List.apply[SourceLevel2](SourceLevel2.apply(1), SourceLevel2.apply(2))))

    (new DestToplevel(level1 =
      new DestLevel1(
        int = source$proxy6.level1.int,
        level2s = source$proxy6.level1.level2s
          .map[DestLevel2]((src: SourceLevel2) => new DestLevel2(value = Some.apply[Int](src.value)))
          .to[Vector[DestLevel2]](iterableFactory[DestLevel2])
      )
    ): DestToplevel): DestToplevel
  }
```
</details>


#### 7. Transforming between enums/sealed traits

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
// res46: OtherPaymentMethod = Cash
```

<details>
  <summary>Click to see the generated code</summary>

``` scala 
  {
    val source$proxy8: PaymentMethod = PaymentMethod.Cash: PaymentMethod

    (if (source$proxy8.isInstanceOf[Card])
       new Card(
         name = source$proxy8.asInstanceOf[Card].name,
         digits = source$proxy8.asInstanceOf[Card].digits,
         expires = source$proxy8.asInstanceOf[Card].expires
       )
     else if (source$proxy8.isInstanceOf[Cash.type]) Cash
     else if (source$proxy8.isInstanceOf[PayPal]) new PayPal(email = source$proxy8.asInstanceOf[PayPal].email)
     else
       throw new RuntimeException(
         "Unhandled condition encountered during Coproduct Transformer derivation"
       ): OtherPaymentMethod): OtherPaymentMethod
  }
```
</details>

#### 8. Same named singletons

Transformations between same named singletons come down to just reffering to the destination singleton.

```scala
object example1 {
  case object Singleton
}

object example2 {
  case object Singleton
}

example1.Singleton.to[example2.Singleton.type]
// res48: Singleton = Singleton
```
<details>
  <summary>Click to see the generated code</summary>

``` scala 
  Singleton
```
</details>

#### 9. Unwrapping a value class

```scala
case class Wrapper1(value: Int) extends AnyVal

Wrapper1(1).to[Int]
// res50: Int = 1
```

<details>
  <summary>Click to see the generated code</summary>

``` scala 
  {
    val source$proxy10: Wrapper1 = Wrapper1.apply(1)

    (source$proxy10.value: Int): Int
  }
```
</details>

#### 10. Wrapping a value class

```scala
case class Wrapper2(value: Int) extends AnyVal

1.to[Wrapper2]
// res52: Wrapper2 = Wrapper2(value = 1)
```

<details>
  <summary>Click to see the generated code</summary>

``` scala 
  ((new Wrapper2(1): Wrapper2): Wrapper2)
```
</details>

#### 11. Automatically derived `Transformer.Derived`

Instances of `Transformer.Derived` are automatically derived as a fallback to support use cases where a generic type (eg. a field of a case class) is unknown at definition site.

Note that `Transformer[A, B] <: Transformer.Derived[A, B]` so any `Transformer` in scope is eligible to become a `Transformer.Derived`.

```scala
final case class Source[A](field1: Int, field2: String, generic: A)
final case class Dest[A](field1: Int, field2: String, generic: A)

def transformSource[A, B](source: Source[A])(using Transformer.Derived[A, B]): Dest[B] = source.to[Dest[B]]

transformSource[Int, Option[Int]](Source(1, "2", 3))
// res54: Dest[Option[Int]] = Dest(
//   field1 = 1,
//   field2 = "2",
//   generic = Some(value = 3)
// )
```

<details>
  <summary>Click to see the generated code</summary>

``` scala 
  {
    def transformSource[A, B](source: Source[A])(x$2: Transformer.Derived[A, B]): Dest[B] =
      (new Dest[B](field1 = source.field1, field2 = source.field2, generic = x$2.transform(source.generic)): Dest[B]): Dest[B]
    transformSource[Int, Option[Int]](Source.apply[Int](1, "2", 3))({
      final class $anon() extends Derived[Int, Option[Int]] {
        def transform(value: Int): Option[Int] = Some.apply[Int](value): Option[Int]
      }

      new $anon(): Derived[Int, Option[Int]]
    }: Derived[Int, Option[Int]])
  }
```
</details>
