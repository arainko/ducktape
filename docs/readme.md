# ![ducktape-logo-32](https://user-images.githubusercontent.com/46346508/236060869-3b118075-f660-44c9-9d0d-d40fba5c8db0.svg) ducktape 0.2.x

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.github.arainko/ducktape_3/badge.svg?style=flat-square)](https://maven-badges.herokuapp.com/maven-central/io.github.arainko/ducktape_3)

*ducktape* is a library for boilerplate-less and configurable transformations between case classes and enums/sealed traits for Scala 3. Directly inspired by [chimney](https://github.com/scalalandio/chimney).

If this project interests you, please drop a 🌟 - these things are worthless but give me a dopamine rush nonetheless.

## Table of contents

```scala mdoc:passthrough
import io.github.arainko.ducktape.docs.*
Docs.generateTableOfContents()
```

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
```scala mdoc
import io.github.arainko.ducktape.*

wirePerson.to[domain.Person]
```
<details>
  <summary>Click to see the generated code</summary>

```scala mdoc:passthrough
  import io.github.arainko.ducktape.docs.*

  Docs.printCode(wirePerson.to[domain.Person])
``` 
</details>

Read more about the rules under which the transformations are generated in ['Transformation rules'](#transfomation-rules).

* `Source#into[Dest]` -  for any two types `Source` and `Dest`, used to create a 'transformation builder' that allows fixing transformation errors and overriding transformations for selected fields or subtypes.

```scala mdoc
import io.github.arainko.ducktape.*

wirePerson
  .into[domain.Person]
  .transform(Field.const(_.paymentMethods.element.at[domain.PaymentMethod.PayPal].email, "overridden@email.com"))
```

<details>
  <summary>Click to see the generated code</summary>

```scala mdoc:passthrough
  import io.github.arainko.ducktape.docs.*

  Docs.printCode(
    wirePerson
      .into[domain.Person]
      .transform(Field.const(_.paymentMethods.element.at[domain.PaymentMethod.PayPal].email, "overridden@email.com"))
  )
``` 
</details>

Read more in the section about [configuring transformations](#configuring-transformations).

* `Source#via(<method reference>)` - for any type `Source` and a `method reference` that can be eta-expanded into a function with named arguments, used to expand the method's argument list with the fields of the `Source` type

```scala mdoc
import io.github.arainko.ducktape.*

wirePerson.via(domain.Person.apply)
```
<details>
  <summary>Click to see the generated code</summary>

```scala mdoc:passthrough
  import io.github.arainko.ducktape.docs.*

  Docs.printCode(wirePerson.via(domain.Person.apply))
``` 
</details>

To read about how these transformations are generated head on over to the section about [transformation rules](#transfomation-rules).

* `Source.intoVia(<method reference>)` - for any type `Source` and a `method reference` that can be eta-expanded into a function with named arguments, used to create a 'transformation builder' that allows fixing transformation errors and overriding transformations for selected fields or subtypes.

```scala mdoc
import io.github.arainko.ducktape.*

wirePerson
  .intoVia(domain.Person.apply)
  .transform(Field.const(_.paymentMethods.element.at[domain.PaymentMethod.PayPal].email, "overridden@email.com"))
```

<details>
  <summary>Click to see the generated code</summary>

```scala mdoc:passthrough
  import io.github.arainko.ducktape.docs.*

  Docs.printCode(
    wirePerson
      .intoVia(domain.Person.apply)
      .transform(Field.const(_.paymentMethods.element.at[domain.PaymentMethod.PayPal].email, "overridden@email.com"))
  )
``` 
</details>

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

```scala mdoc
wirePerson
  .into[domain.Person]
  .transform(
    Field.const(_.age, 24),
    Case.const(_.paymentMethods.element.at[wire.PaymentMethod.Transfer], domain.PaymentMethod.Cash)
  )
```
<details>
  <summary>Click to see the generated code</summary>

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

```scala mdoc:silent
case class PaymentBand(name: String, digits: Long, color: String = "red")

val card: wire.PaymentMethod.Card = 
  wire.PaymentMethod.Card(name = "J. Doe", digits = 213712345)
```

* `Field.const` - allows to supply a constant value for a given field
```scala mdoc
card
  .into[PaymentBand]
  .transform(Field.const(_.color, "blue"))
```
<details>
  <summary>Click to see the generated code</summary>

```scala mdoc:passthrough
  import io.github.arainko.ducktape.docs.*

  Docs.printCode(
   card
    .into[PaymentBand]
    .transform(Field.const(_.color, "blue"))
  )
``` 
</details>

* `Field.computed` - allows to compute a value with a function the shape of `Dest => FieldTpe`
```scala mdoc
card
  .into[PaymentBand]
  .transform(
    Field.computed(_.color, card => if (card.digits % 2 == 0) "green" else "yellow")
  )
```
<details>
  <summary>Click to see the generated code</summary>

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
</details>

* `Field.default` - only works when a field's got a default value defined (defaults are not taken into consideration by default)

```scala mdoc
card
  .into[PaymentBand]
  .transform(Field.default(_.color))
```
<details>
  <summary>Click to see the generated code</summary>

```scala mdoc:passthrough
  import io.github.arainko.ducktape.docs.*

  Docs.printCode(
   card
    .into[PaymentBand]
    .transform(Field.default(_.color))
  )
``` 
</details>

* `Field.allMatching` - allow to supply a field source whose fields will replace all matching fields in the destination (given that the names and the types match up)

```scala mdoc:silent
case class FieldSource(color: String, digits: Long, extra: Int)
val source = FieldSource("magenta", 123445678, 23)
```

```scala mdoc
card
  .into[PaymentBand]
  .transform(Field.allMatching(paymentBand => paymentBand, source))
```
<details>
  <summary>Click to see the generated code</summary>

```scala mdoc:passthrough
  import io.github.arainko.ducktape.docs.*

  Docs.printCode(
  card
    .into[PaymentBand]
    .transform(Field.allMatching(paymentBand => paymentBand, source))
  )
``` 
</details>

* `Field.fallbackToDefault` - falls back to default field values but ONLY in case a transformation cannot be created
```scala mdoc:nest:silent
case class SourceToplevel(level1: SourceLevel1, transformableButWithDefault: Int)
case class SourceLevel1(str: String)

case class DestToplevel(level1: DestLevel1, extra: Int = 111, transformableButWithDefault: Int = 3000)
case class DestLevel1(extra: String = "level1", str: String)

val source = SourceToplevel(SourceLevel1("str"), 400)
```
```scala mdoc
source
  .into[DestToplevel]
  .transform(Field.fallbackToDefault)
```

<details>
  <summary>Click to see the generated code</summary>

```scala mdoc:passthrough
  import io.github.arainko.ducktape.docs.*

Docs.printCode(
  source
    .into[DestToplevel]
    .transform(Field.fallbackToDefault)
)
``` 
</details>

`Field.fallbackToDefault` is a `regional` config, which means that you can control the scope where it applies:

```scala mdoc
source
  .into[DestToplevel]
  .transform(
    Field.fallbackToDefault.regional(_.level1), // <-- we're applying the config starting on the `.level1` field and below, it'll be also applied to other transformations nested inside
    Field.const(_.extra, 123) // <-- note that this field now needs to be configured manually
  )
```

<details>
  <summary>Click to see the generated code</summary>

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
</details>

* `Field.fallbackToNone` - falls back to `None` for `Option` fields for which a transformation cannot be created

```scala mdoc:nest:silent
case class SourceToplevel(level1: SourceLevel1, transformable: Option[Int])
case class SourceLevel1(str: String)

case class DestToplevel(level1: DestLevel1, extra: Option[Int], transformable: Option[Int])
case class DestLevel1(extra: Option[String], str: String)

val source = SourceToplevel(SourceLevel1("str"), Some(400))
```

```scala mdoc
source
  .into[DestToplevel]
  .transform(Field.fallbackToNone)
```

<details>
  <summary>Click to see the generated code</summary>

```scala mdoc:passthrough
  import io.github.arainko.ducktape.docs.*

Docs.printCode(
  source
    .into[DestToplevel]
    .transform(Field.fallbackToNone)
)
``` 
</details>

`Field.fallbackToNone` is a `regional` config, which means that you can control the scope where it applies:

```scala mdoc
source
  .into[DestToplevel]
  .transform(
    Field.fallbackToNone.regional(_.level1), // <-- we're applying the config starting on the `.level1` field and below, it'll be also applied to other transformations nested inside
    Field.const(_.extra, Some(123)) // <-- note that this field now needs to be configured manually
  )
```

<details>
  <summary>Click to see the generated code</summary>

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
</details>

### Coproduct configurations

```scala mdoc
val transfer = wire.PaymentMethod.Transfer("2764262")
```

* `Case.const` - allows to supply a constant value for a given subtype of a coproduct
```scala mdoc
transfer
  .into[domain.PaymentMethod]
  .transform(Case.const(_.at[wire.PaymentMethod.Transfer], domain.PaymentMethod.Cash))
```
<details>
  <summary>Click to see the generated code</summary>

```scala mdoc:passthrough
  import io.github.arainko.ducktape.docs.*

  Docs.printCode(
  transfer
    .into[domain.PaymentMethod]
    .transform(Case.const(_.at[wire.PaymentMethod.Transfer], domain.PaymentMethod.Cash))
  )
``` 
</details>


* `Case.computed` - allow to supply a function of the selected source type to the expected destination type
```scala mdoc
transfer
  .into[domain.PaymentMethod]
  .transform(
    Case.computed(_.at[wire.PaymentMethod.Transfer], transfer => domain.PaymentMethod.Card("J. Doe", transfer.accountNo.toLong))
  )
```

<details>
  <summary>Click to see the generated code</summary>

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
</details>

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

## Transfomation rules

Let's go over the priority and rules that `ducktape` uses to create a transformation (in the same order they're tried in the implementation):

#### 1. User supplied `Transformers`

Custom instances of a `Transfomer` are always prioritized since these also function as an extension mechanism of the library.

```scala mdoc
given Transformer[String, List[String]] = str => str :: Nil

"single value".to[List[String]]
```

<details>
  <summary>Click to see the generated code</summary>

```scala mdoc:passthrough
  import io.github.arainko.ducktape.docs.*

  Docs.printCode("single value".to[List[String]])
``` 
</details>

#### 2. Upcasting

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

#### 3. Mapping over an `Option`

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

#### 4. Transforming and wrapping in an `Option`

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

#### 5. Mapping over and changing the collection type

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

#### 6. Transforming between case classes

A source case class can be transformed into the destination case class given that:
* source has fields whose names cover all of the destination's fields,
* a transformation for the types corresponding to those fields can be derived.

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
<details>
  <summary>Click to see the generated code</summary>

```scala mdoc:passthrough
  import io.github.arainko.ducktape.docs.*

  Docs.printCode(
    SourceToplevel(SourceLevel1("extra", 1, List(SourceLevel2(1), SourceLevel2(2)))).to[DestToplevel]
    )
``` 
</details>


#### 7. Transforming between enums/sealed traits

A source coproduct can be transformed into the destination coproduct given that:
* destination's children have names that match all of the source's children,
* a transformation between those two corresponding types can be derived.

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

<details>
  <summary>Click to see the generated code</summary>

```scala mdoc:passthrough
  import io.github.arainko.ducktape.docs.*

  Docs.printCode((PaymentMethod.Cash: PaymentMethod).to[OtherPaymentMethod])
``` 
</details>

#### 8. Same named singletons

Transformations between same named singletons come down to just reffering to the destination singleton.

```scala mdoc
object example1 {
  case object Singleton
}

object example2 {
  case object Singleton
}

example1.Singleton.to[example2.Singleton.type]
```
<details>
  <summary>Click to see the generated code</summary>

```scala mdoc:passthrough
  import io.github.arainko.ducktape.docs.*

  Docs.printCode(example1.Singleton.to[example2.Singleton.type])
``` 
</details>

#### 9. Unwrapping a value class

```scala mdoc
case class Wrapper1(value: Int) extends AnyVal

Wrapper1(1).to[Int]
```

<details>
  <summary>Click to see the generated code</summary>

```scala mdoc:passthrough
  import io.github.arainko.ducktape.docs.*

  Docs.printCode(Wrapper1(1).to[Int])
``` 
</details>

#### 10. Wrapping a value class

```scala mdoc
case class Wrapper2(value: Int) extends AnyVal

1.to[Wrapper2]
```

<details>
  <summary>Click to see the generated code</summary>

```scala mdoc:passthrough
  import io.github.arainko.ducktape.docs.*

  Docs.printCode(1.to[Wrapper2])
``` 
</details>

#### 11. Automatically derived `Transformer.Derived`

Instances of `Transformer.Derived` are automatically derived as a fallback to support use cases where a generic type (eg. a field of a case class) is unknown at definition site.

Note that `Transformer[A, B] <: Transformer.Derived[A, B]` so any `Transformer` in scope is eligible to become a `Transformer.Derived`.

```scala mdoc
final case class Source[A](field1: Int, field2: String, generic: A)
final case class Dest[A](field1: Int, field2: String, generic: A)

def transformSource[A, B](source: Source[A])(using Transformer.Derived[A, B]): Dest[B] = source.to[Dest[B]]

transformSource[Int, Option[Int]](Source(1, "2", 3))
```

<details>
  <summary>Click to see the generated code</summary>

```scala mdoc:passthrough
  import io.github.arainko.ducktape.docs.*

  Docs.printCode {
    def transformSource[A, B](source: Source[A])(using Transformer.Derived[A, B]): Dest[B]  =  source.to[Dest[B]]

    transformSource[Int, Option[Int]](Source(1, "2", 3))
  }
``` 
</details>
