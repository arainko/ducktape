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

@:select(underlying-code)

@:choice(visible)
```scala mdoc
val domainPerson = wirePerson.to[domain.Person]
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

@:select(underlying-code)

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

## Basics

```scala
// Entrypoint to the library
import io.github.arainko.ducktape.*
```

The import above brings in a number of extension methods, let's examine how these work by redefining a simplified version of the model first seen in the [motivating example](#motivating-example):

```scala mdoc:reset:silent
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


@:select(underlying-code)
@:choice(visible)
```scala mdoc
import io.github.arainko.ducktape.*

wirePerson.to[domain.Person]
```
@:choice(generated)
```scala mdoc:passthrough
  import io.github.arainko.ducktape.docs.*

  Docs.printCode(wirePerson.to[domain.Person])
``` 
@:@

Read more about the rules under which the transformations are generated in ['Transformation rules'](#transfomation-rules).

* `Source#into[Dest]` -  for any two types `Source` and `Dest`, used to create a 'transformation builder' that allows fixing transformation errors and overriding transformations for selected fields or subtypes.

@:select(underlying-code)
@:choice(visible)
```scala mdoc
import io.github.arainko.ducktape.*

wirePerson
  .into[domain.Person]
  .transform(Field.const(_.paymentMethods.element.at[domain.PaymentMethod.PayPal].email, "overridden@email.com"))
```
@:choice(generated)
```scala mdoc:passthrough
  import io.github.arainko.ducktape.docs.*

  Docs.printCode(
    wirePerson
      .into[domain.Person]
      .transform(Field.const(_.paymentMethods.element.at[domain.PaymentMethod.PayPal].email, "overridden@email.com"))
  )
``` 
@:@

Read more in the section about [configuring transformations](#configuring-transformations).

* `Source#via(<method reference>)` - for any type `Source` and a `method reference` that can be eta-expanded into a function with named arguments, used to expand the method's argument list with the fields of the `Source` type

@:select(underlying-code)
@:choice(visible)
```scala mdoc
import io.github.arainko.ducktape.*

wirePerson.via(domain.Person.apply)
```
@:choice(generated)
```scala mdoc:passthrough
  import io.github.arainko.ducktape.docs.*

  Docs.printCode(wirePerson.via(domain.Person.apply))
``` 
@:@

To read about how these transformations are generated head on over to the section about [transformation rules](#transfomation-rules).

* `Source.intoVia(<method reference>)` - for any type `Source` and a `method reference` that can be eta-expanded into a function with named arguments, used to create a 'transformation builder' that allows fixing transformation errors and overriding transformations for selected fields or subtypes.

@:select(underlying-code)
@:choice(visible)
```scala mdoc
import io.github.arainko.ducktape.*

wirePerson
  .intoVia(domain.Person.apply)
  .transform(Field.const(_.paymentMethods.element.at[domain.PaymentMethod.PayPal].email, "overridden@email.com"))
```

@:choice(generated)
```scala mdoc:passthrough
  import io.github.arainko.ducktape.docs.*

  Docs.printCode(
    wirePerson
      .intoVia(domain.Person.apply)
      .transform(Field.const(_.paymentMethods.element.at[domain.PaymentMethod.PayPal].email, "overridden@email.com"))
  )
``` 
@:@

Read more in the section about [configuring transformations](#configuring-transformations).

## Configuring transformations

### Introduction and explanation

Transformations can customized or 'fixed' with a slew of configuration options, let's examine a quick example based on a slightly modified version of the previously introduced model:

```scala mdoc:reset-object:silent
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
```scala mdoc:fail
wirePerson.to[domain.Person]
```

The newly added field (`age`) and enum case (`PaymentMethod.Transfer`) do not have a corresponding mapping, let's say we want to set the age field to a constant value of 24 and when a PaymentMethod.Transfer is encountered we map it to `Cash` instead.

@:select(underlying-code)
@:choice(visible)
```scala mdoc
wirePerson
  .into[domain.Person]
  .transform(
    Field.const(_.age, 24),
    Case.const(_.paymentMethods.element.at[wire.PaymentMethod.Transfer], domain.PaymentMethod.Cash)
  )
```
@:choice(generated)
```scala mdoc:passthrough
  import io.github.arainko.ducktape.docs.*

  Docs.printCode(
   wirePerson
    .into[domain.Person]
    .transform(
      Field.const(_.age, 24),
      Case.const(_.paymentMethods.element.at[wire.PaymentMethod.Transfer], domain.PaymentMethod.Cash)
    )
  )
``` 
@:@

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

```scala mdoc:silent
case class PaymentBand(name: String, digits: Long, color: String = "red")

val card: wire.PaymentMethod.Card = 
  wire.PaymentMethod.Card(name = "J. Doe", digits = 213712345)
```

* `Field.const` - allows to supply a constant value for a given field

@:select(underlying-code)
@:choice(visible)
```scala mdoc
card
  .into[PaymentBand]
  .transform(Field.const(_.color, "blue"))
```
@:choice(generated)
```scala mdoc:passthrough
  import io.github.arainko.ducktape.docs.*

  Docs.printCode(
   card
    .into[PaymentBand]
    .transform(Field.const(_.color, "blue"))
  )
``` 
@:@

* `Field.computed` - allows to compute a value with a function the shape of `Dest => FieldTpe`

@:select(underlying-code)
@:choice(visible)
```scala mdoc
card
  .into[PaymentBand]
  .transform(
    Field.computed(_.color, card => if (card.digits % 2 == 0) "green" else "yellow")
  )
```
@:choice(generated)
```scala mdoc:passthrough
  import io.github.arainko.ducktape.docs.*

  Docs.printCode(
   card
    .into[PaymentBand]
    .transform(
      Field.computed(_.color, card => if (card.digits % 2 == 0) "green" else "yellow")
    )
  )
``` 
@:@

* `Field.default` - only works when a field's got a default value defined (defaults are not taken into consideration by default)

@:select(underlying-code)
@:choice(visible)
```scala mdoc
card
  .into[PaymentBand]
  .transform(Field.default(_.color))
```
@:choice(generated)
```scala mdoc:passthrough
  import io.github.arainko.ducktape.docs.*

  Docs.printCode(
   card
    .into[PaymentBand]
    .transform(Field.default(_.color))
  )
``` 
@:@

* `Field.allMatching` - allow to supply a field source whose fields will replace all matching fields in the destination (given that the names and the types match up)

```scala mdoc:silent
case class FieldSource(color: String, digits: Long, extra: Int)
val source = FieldSource("magenta", 123445678, 23)
```

@:select(underlying-code)
@:choice(visible)
```scala mdoc
card
  .into[PaymentBand]
  .transform(Field.allMatching(paymentBand => paymentBand, source))
```
@:choice(generated)
```scala mdoc:passthrough
  import io.github.arainko.ducktape.docs.*

  Docs.printCode(
  card
    .into[PaymentBand]
    .transform(Field.allMatching(paymentBand => paymentBand, source))
  )
``` 
@:@

* `Field.fallbackToDefault` - falls back to default field values but ONLY in case a transformation cannot be created

```scala mdoc:nest:silent
case class SourceToplevel(level1: SourceLevel1, transformableButWithDefault: Int)
case class SourceLevel1(str: String)

case class DestToplevel(level1: DestLevel1, extra: Int = 111, transformableButWithDefault: Int = 3000)
case class DestLevel1(extra: String = "level1", str: String)

val source = SourceToplevel(SourceLevel1("str"), 400)
```

@:select(underlying-code)
@:choice(visible)
```scala mdoc
source
  .into[DestToplevel]
  .transform(Field.fallbackToDefault)
```

@:choice(generated)
```scala mdoc:passthrough
import io.github.arainko.ducktape.docs.*

Docs.printCode(
  source
    .into[DestToplevel]
    .transform(Field.fallbackToDefault)
)
``` 
@:@

`Field.fallbackToDefault` is a `regional` config, which means that you can control the scope where it applies:

@:select(underlying-code)
@:choice(visible)
```scala mdoc
source
  .into[DestToplevel]
  .transform(
    Field.fallbackToDefault.regional(_.level1), // <-- we're applying the config starting on the `.level1` field and below, it'll be also applied to other transformations nested inside
    Field.const(_.extra, 123) // <-- note that this field now needs to be configured manually
  )
```
@:choice(generated)
```scala mdoc:passthrough
import io.github.arainko.ducktape.docs.*

Docs.printCode(
  source
    .into[DestToplevel]
    .transform(
      Field.fallbackToDefault.regional(_.level1), // <-- we're applying the config starting on the `.level1` field and below, it'll be also applied to other transformations nested inside
      Field.const(_.extra, 123)
    )
)
```
@:@

* `Field.fallbackToNone` - falls back to `None` for `Option` fields for which a transformation cannot be created

```scala mdoc:nest:silent
case class SourceToplevel(level1: SourceLevel1, transformable: Option[Int])
case class SourceLevel1(str: String)

case class DestToplevel(level1: DestLevel1, extra: Option[Int], transformable: Option[Int])
case class DestLevel1(extra: Option[String], str: String)

val source = SourceToplevel(SourceLevel1("str"), Some(400))
```

@:select(underlying-code)
@:choice(visible)
```scala mdoc
source
  .into[DestToplevel]
  .transform(Field.fallbackToNone)
```

@:choice(generated)
```scala mdoc:passthrough
  import io.github.arainko.ducktape.docs.*

Docs.printCode(
  source
    .into[DestToplevel]
    .transform(Field.fallbackToNone)
)
``` 
@:@

`Field.fallbackToNone` is a `regional` config, which means that you can control the scope where it applies:

@:select(underlying-code)
@:choice(visible)
```scala mdoc
source
  .into[DestToplevel]
  .transform(
    Field.fallbackToNone.regional(_.level1), // <-- we're applying the config starting on the `.level1` field and below, it'll be also applied to other transformations nested inside
    Field.const(_.extra, Some(123)) // <-- note that this field now needs to be configured manually
  )
```
@:choice(generated)
```scala mdoc:passthrough
  import io.github.arainko.ducktape.docs.*

Docs.printCode(
  source
  .into[DestToplevel]
  .transform(
    Field.fallbackToNone.regional(_.level1),
    Field.const(_.extra, Some(123))
  )
)
``` 
@:@

### Coproduct configurations

```scala mdoc
val transfer = wire.PaymentMethod.Transfer("2764262")
```

* `Case.const` - allows to supply a constant value for a given subtype of a coproduct

@:select(underlying-code)
@:choice(visible)
```scala mdoc
transfer
  .into[domain.PaymentMethod]
  .transform(Case.const(_.at[wire.PaymentMethod.Transfer], domain.PaymentMethod.Cash))
```

@:choice(generated)
```scala mdoc:passthrough
  import io.github.arainko.ducktape.docs.*

  Docs.printCode(
  transfer
    .into[domain.PaymentMethod]
    .transform(Case.const(_.at[wire.PaymentMethod.Transfer], domain.PaymentMethod.Cash))
  )
``` 
@:@

* `Case.computed` - allow to supply a function of the selected source type to the expected destination type

@:select(underlying-code)
@:choice(visible)
```scala mdoc
transfer
  .into[domain.PaymentMethod]
  .transform(
    Case.computed(_.at[wire.PaymentMethod.Transfer], transfer => domain.PaymentMethod.Card("J. Doe", transfer.accountNo.toLong))
  )
```
@:choice(generated)
```scala mdoc:passthrough
  import io.github.arainko.ducktape.docs.*

  Docs.printCode(
  transfer
    .into[domain.PaymentMethod]
    .transform(
      Case.computed(_.at[wire.PaymentMethod.Transfer], transfer => domain.PaymentMethod.Card("J. Doe", transfer.accountNo.toLong))
    )
  )
``` 
@:@

### Specifics and limitations

* Configs can override transformations
```scala mdoc
wirePerson
  .into[domain.Person]
  .transform(
    Field.const(_.age, 24),
    Case.const(_.paymentMethods.element.at[wire.PaymentMethod.Transfer], domain.PaymentMethod.Cash),
    Field.const(_.paymentMethods.element, domain.PaymentMethod.Cash) // <-- override all payment methods to `Cash`
  )
```

* Configs can override each other

```scala mdoc
wirePerson
  .into[domain.Person]
  .transform(
    Case.const(_.paymentMethods.element.at[wire.PaymentMethod.Transfer], domain.PaymentMethod.Cash),
    Field.const(_.age, 24),
    Field.const(_.age, 50) // <-- override the previously configured 'age' field`
  )
```

* Config on a field or a case 'above' overrides the configs 'below'
```scala mdoc
wirePerson
  .into[domain.Person]
  .transform(
    Field.const(_.age, 24),
    Case.const(_.paymentMethods.element.at[wire.PaymentMethod.Transfer], domain.PaymentMethod.Cash),
    Field.const(_.paymentMethods.element, domain.PaymentMethod.Cash), // <-- override all payment methods to `Cash`,
    Field.const(_.paymentMethods, Vector.empty[domain.PaymentMethod]) // <-- also override the 'parent' of '_.paymentMethods.element' so now payment methods are just empty
  )
```

However, first configuring the field a level above and then the field a level below is not supported:
```scala mdoc:fail
wirePerson
  .into[domain.Person]
  .transform(
    Field.const(_.age, 24),
    Case.const(_.paymentMethods.element.at[wire.PaymentMethod.Transfer], domain.PaymentMethod.Cash),
    Field.const(_.paymentMethods, Vector.empty[domain.PaymentMethod]), // <-- configure the field a level above first
    Field.const(_.paymentMethods.element, domain.PaymentMethod.Cash), // <-- then the field below it
  )
```

## Fallible transfomations
Sometimes ordinary field mappings just do not cut it, more often than not our domain model's constructors are hidden behind a safe factory method, eg.:

```scala mdoc:reset
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

```scala mdoc

final case class UnvalidatedPerson(name: String, age: Int, socialSecurityNo: String)

val unvalidatedPerson = UnvalidatedPerson("ValidName", -1, "SSN")

val transformed = unvalidatedPerson.via(Person.create)
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

```scala mdoc
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

```scala mdoc:nest
case class Person(name: Name, age: Age, socialSecurityNo: SSN)

object Name extends NewtypeValidated[String](str => !str.isBlank, "Name should not be blank!")
type Name = Name.Type

object Age extends NewtypeValidated[Int](int => int > 0, "Age should be positive!")
type Age = Age.Type

object SSN extends NewtypeValidated[String](str => str.length > 5, "SSN should be longer than 5!")
type SSN = SSN.Type
```

We introduce a newtype for each field, this way we can keep our invariants at compiletime and also let `ducktape` do its thing.

```scala mdoc:silent
// this should trip up our validation
val bad = UnvalidatedPerson(name = "", age = -1, socialSecurityNo = "SOCIALNO")

// this one should pass
val good = UnvalidatedPerson(name = "ValidName", age = 24, socialSecurityNo = "SOCIALNO")
```

Instances of `Transformer.Fallible` wrapped in some type `F` are derived automatically for case classes given that a `Mode.Accumulating` instance exists for `F` and all of the fields of the source type have a corresponding counterpart in the destination type and each one of them has an instance of either `Transformer.Fallible` or a total `Transformer` in scope.

@:select(underlying-code)
@:choice(visible)
```scala mdoc
given Mode.Accumulating.Either[String, List] with {}

bad.fallibleTo[Person]
good.fallibleTo[Person]
```
@:choice(generated)
```scala mdoc:passthrough
import io.github.arainko.ducktape.docs.*

Docs.printCode(bad.fallibleTo[Person])
``` 
@:@

Same goes for instances that do fail fast transformations (you need `Mode.FailFast[F]` in scope in this case)

@:select(underlying-code)
@:choice(visible)
```scala mdoc:nest
given Mode.FailFast.Either[String] with {}

bad.fallibleTo[Person]
good.fallibleTo[Person]
```
@:choice(generated)
```scala mdoc:passthrough
Docs.printCode(bad.fallibleTo[Person])
```
@:@

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

@:select(underlying-code)
@:choice(visible)
```scala mdoc:nest
given Mode.Accumulating.Either[String, List] with {}

bad
  .into[Person]
  .fallible
  .transform(
    Field.fallibleConst(_.name, Name.makeAccumulating("ConstValidName")),
    Field.fallibleConst(_.age, Age.makeAccumulating(25))
  )
```
@:choice(generated)
```scala mdoc:passthrough
Docs.printCode(
  bad
  .into[Person]
  .fallible
  .transform(
    Field.fallibleConst(_.name, Name.makeAccumulating("ConstValidName")),
    Field.fallibleConst(_.age, Age.makeAccumulating(25))
  )
)
```
@:@

* `Field.fallibleComputed` - a fallible variant of `Field.computed` that allows for supplying functions that return values wrapped in an `F`


@:select(underlying-code)
@:choice(visible)
```scala mdoc:nest
given Mode.Accumulating.Either[String, List] with {}

bad
  .into[Person]
  .fallible
  .transform(
    Field.fallibleComputed(_.name, uvp => Name.makeAccumulating(uvp.name + "ConstValidName")),
    Field.fallibleComputed(_.age, uvp => Age.makeAccumulating(uvp.age + 25))
  )
```
@:choice(generated)
```scala mdoc:passthrough
Docs.printCode(
  bad
    .into[Person]
    .fallible
    .transform(
      Field.fallibleComputed(_.name, uvp => Name.makeAccumulating(uvp.name + "ConstValidName")),
      Field.fallibleComputed(_.age, uvp => Age.makeAccumulating(uvp.age + 25))
    )
)
```
@:@

#### Fallible coproduct configurations

Let's define a wire enum (pretend that it's coming from... somewhere) and a domain enum that doesn't exactly align with the wire one.
```scala mdoc:nest
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

@:select(underlying-code)
@:choice(visible)
```scala mdoc:nest
given Mode.FailFast.Either[String] with {}

wire.ReleaseKind.Single
  .into[domain.ReleaseKind]
  .fallible
  .transform(
    Case.fallibleConst(_.at[wire.ReleaseKind.Single.type], Left("Unsupported release kind"))
  )
```
@:choice(generated)
```scala mdoc:passthrough
Docs.printCode(
  wire.ReleaseKind.Single
    .into[domain.ReleaseKind]
    .fallible
    .transform(
      Case.fallibleConst(_.at[wire.ReleaseKind.Single.type], Left("Unsupported release kind"))
    )
)
```
@:@

* `Case.fallibleComputed` - a fallible variant of `Case.computed` that allows for supplying functions that return values wrapped in an `F`

@:select(underlying-code)
@:choice(visible)
```scala mdoc:nest
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
```
@:choice(generated)
```scala mdoc:passthrough
Docs.printCode(
  wire.ReleaseKind.Single
    .into[domain.ReleaseKind]
    .fallible
    .transform(
      Case.fallibleComputed(_.at[wire.ReleaseKind.Single.type], handleSingle)
    )
)
```
@:@

### Building custom instances of fallible transformers
Life is not always lolipops and crisps and sometimes you need to write a typeclass instance by hand. Worry not though, just like in the case of total transformers, we can easily define custom instances with the help of the configuration DSL (which, let's write it down once again, is a superset of total transformers' DSL).

By all means go wild with the configuration options, I'm too lazy to write them all out here again.

@:select(underlying-code)
@:choice(visible)
```scala mdoc:nest:silent
given Mode.Accumulating.Either[String, List] with {}

val customAccumulating =
  Transformer
    .define[UnvalidatedPerson, Person]
    .fallible
    .build(
      Field.fallibleConst(_.name, Name.makeAccumulating("IAmAlwaysValidNow!"))
    )
```
@:choice(generated)
```scala mdoc:passthrough
Docs.printCode(
  Transformer
    .define[UnvalidatedPerson, Person]
    .fallible
    .build(
      Field.fallibleConst(_.name, Name.makeAccumulating("IAmAlwaysValidNow!"))
    )
)
```
@:@

And for the ones that are not keen on writing out method arguments:

@:select(underlying-code)
@:choice(visible)
```scala mdoc:nest:silent
given Mode.Accumulating.Either[String, List] with {}

val customAccumulatingVia =
  Transformer
    .defineVia[UnvalidatedPerson](Person.apply)
    .fallible
    .build(
      Field.fallibleConst(_.name, Name.makeAccumulating("IAmAlwaysValidNow!"))
    )
```
@:choice(generated)
```scala mdoc:passthrough
Docs.printCode(
  Transformer
    .defineVia[UnvalidatedPerson](Person.apply)
    .fallible
    .build(
      Field.fallibleConst(_.name, Name.makeAccumulating("IAmAlwaysValidNow!"))
    )
)
```
@:@


## Transfomation rules

Let's go over the priority and rules that `ducktape` uses to create a transformation (in the same order they're tried in the implementation):

1. User supplied `Transformers`

Custom instances of a `Transfomer` are always prioritized since these also function as an extension mechanism of the library.

@:select(underlying-code)
@:choice(visible)
```scala mdoc
given Transformer[String, List[String]] = str => str :: Nil

"single value".to[List[String]]
```
@:choice(generated)
```scala mdoc:passthrough
  import io.github.arainko.ducktape.docs.*

  Docs.printCode("single value".to[List[String]])
``` 
@:@

2. Upcasting

Transforming a type to its supertype is just an upcast.
@:select(underlying-code)
@:choice(visible)
```scala mdoc
// (Int | String) >: Int
1.to[Int | String]
```
@:choice(generated)
```scala mdoc:passthrough
  Docs.printCode(1.to[Int | String])
``` 
@:@

3. Mapping over an `Option`

Transforming between options comes down to mapping over it and recursively deriving a transformation for the value inside.

@:select(underlying-code)
@:choice(visible)
```scala mdoc
given Transformer[Int, String] = int => int.toString

Option(1).to[Option[String]]
```
@:choice(generated)
```scala mdoc:passthrough
  Docs.printCode(Option(1).to[Option[String]])
``` 
@:@

4. Transforming and wrapping in an `Option`

If a transformation between two types is possible then transforming between the source type and an `Option` of the destination type is just wrapping the transformation result in a `Some`.

@:select(underlying-code)
@:choice(visible)
```scala mdoc
1.to[Option[Int | String]]
```
@:choice(generated)
```scala mdoc:passthrough
  Docs.printCode(1.to[Option[Int | String]])
``` 
@:@
5. Mapping over and changing the collection type

@:select(underlying-code)
@:choice(visible)
```scala mdoc:nest
//`.to` is already a method on collections
import io.github.arainko.ducktape.to as convertTo

List(1, 2, 3, 4).convertTo[Vector[Int | String]]
```
@:choice(generated)
```scala mdoc:passthrough
  Docs.printCode(List(1, 2, 3, 4).convertTo[Vector[Int | String]])
``` 
@:@

6. Transforming between case classes

A source case class can be transformed into the destination case class given that:
* source has fields whose names cover all of the destination's fields,
* a transformation for the types corresponding to those fields can be derived.

@:select(underlying-code)
@:choice(visible)
```scala mdoc:reset-object
import io.github.arainko.ducktape.*

case class SourceToplevel(level1: SourceLevel1)
case class SourceLevel1(extra: String, int: Int, level2s: List[SourceLevel2])
case class SourceLevel2(value: Int)

case class DestToplevel(level1: DestLevel1)
case class DestLevel1(int: Int | String, level2s: Vector[DestLevel2])
case class DestLevel2(value: Option[Int])

SourceToplevel(SourceLevel1("extra", 1, List(SourceLevel2(1), SourceLevel2(2)))).to[DestToplevel]
```
@:choice(generated)
```scala mdoc:passthrough
  import io.github.arainko.ducktape.docs.*

  Docs.printCode(
    SourceToplevel(SourceLevel1("extra", 1, List(SourceLevel2(1), SourceLevel2(2)))).to[DestToplevel]
    )
``` 
@:@

7. Transforming between enums/sealed traits

A source coproduct can be transformed into the destination coproduct given that:
* destination's children have names that match all of the source's children,
* a transformation between those two corresponding types can be derived.

@:select(underlying-code)
@:choice(visible)
```scala mdoc
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
```
@:choice(generated)
```scala mdoc:passthrough
  import io.github.arainko.ducktape.docs.*

  Docs.printCode((PaymentMethod.Cash: PaymentMethod).to[OtherPaymentMethod])
``` 
@:@

8. Same named singletons

Transformations between same named singletons come down to just reffering to the destination singleton.

@:select(underlying-code)
@:choice(visible)
```scala mdoc
object example1 {
  case object Singleton
}

object example2 {
  case object Singleton
}

example1.Singleton.to[example2.Singleton.type]
```
@:choice(generated)
```scala mdoc:passthrough
  import io.github.arainko.ducktape.docs.*

  Docs.printCode(example1.Singleton.to[example2.Singleton.type])
``` 
@:@

9. Unwrapping a value class

@:select(underlying-code)
@:choice(visible)
```scala mdoc
case class Wrapper1(value: Int) extends AnyVal

Wrapper1(1).to[Int]
```
@:choice(generated)
```scala mdoc:passthrough
  import io.github.arainko.ducktape.docs.*

  Docs.printCode(Wrapper1(1).to[Int])
``` 
@:@
10. Wrapping a value class

@:select(underlying-code)
@:choice(visible)
```scala mdoc
case class Wrapper2(value: Int) extends AnyVal

1.to[Wrapper2]
```
@:choice(generated)
```scala mdoc:passthrough
  import io.github.arainko.ducktape.docs.*

  Docs.printCode(1.to[Wrapper2])
``` 
@:@

11. Automatically derived `Transformer.Derived`

Instances of `Transformer.Derived` are automatically derived as a fallback to support use cases where a generic type (eg. a field of a case class) is unknown at definition site.

Note that `Transformer[A, B] <: Transformer.Derived[A, B]` so any `Transformer` in scope is eligible to become a `Transformer.Derived`.

@:select(underlying-code)
@:choice(visible)
```scala mdoc
final case class Source[A](field1: Int, field2: String, generic: A)
final case class Dest[A](field1: Int, field2: String, generic: A)

def transformSource[A, B](source: Source[A])(using Transformer.Derived[A, B]): Dest[B] = source.to[Dest[B]]

transformSource[Int, Option[Int]](Source(1, "2", 3))
```
@:choice(generated)
```scala mdoc:passthrough
  import io.github.arainko.ducktape.docs.*

  Docs.printCode {
    def transformSource[A, B](source: Source[A])(using Transformer.Derived[A, B]): Dest[B]  =  source.to[Dest[B]]

    transformSource[Int, Option[Int]](Source(1, "2", 3))
  }
``` 
@:@

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
