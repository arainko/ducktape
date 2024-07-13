## Basics

### Entrypoint

Much as in the case of [total transformations](../total_transformations/basics.md) the entry point of fallible transformations is just single import that brings in a bunch of extension methods:

```scala mdoc
import io.github.arainko.ducktape.*
```

### Introduction

Sometimes our domains are modeled with refinement types (i.e. instead of using a plain `String` we declare a `NonEmptyString` that exposes a smart constructor that enforces certain invariants throughout the app) and fallible transformations are specifically geared towards making that usecase as lighweight as possible. Let's introduce a wire/domain pair of models that makes use of this pattern:

@:select(fallible-model)

@:choice(wire)
```scala mdoc
object wire:
  final case class Person(
    firstName: String,
    lastName: String,
    paymentMethods: List[wire.PaymentMethod]
  )

  enum PaymentMethod:
    case Card(name: String, digits: Long)
    case PayPal(email: String)
    case Cash
```

@:choice(domain)
```scala mdoc
import newtypes.*

object domain:
  final case class Person(
    firstName: NonEmptyString,
    lastName: NonEmptyString,
    paymentMethods: Vector[domain.PaymentMethod]
  )

  enum PaymentMethod:
    case PayPal(email: NonEmptyString)
    case Card(digits: Positive, name: NonEmptyString)
    case Cash
```
@:choice(newtypes)
```scala mdoc
object newtypes:
  opaque type NonEmptyString <: String = String

  object NonEmptyString:
    def create(value: String): Either[String, NonEmptyString] =
      Either.cond(!value.isBlank, value, s"not a non-empty string")

// expand the 'create' method into an instance of Transformer.Fallible
// this is a key component in making those transformations automatic
    given failFast: Transformer.Fallible[[a] =>> Either[String, a], String, NonEmptyString] =
      create

// also declare the same fallible transformer but make it ready for error accumulation
    given accumulating: Transformer.Fallible[[a] =>> Either[List[String], a], String, NonEmptyString] =
      create(_).left.map(_ :: Nil)

  opaque type Positive <: Long = Long

  object Positive:
    def create(value: Long): Either[String, Positive] =
      Either.cond(value > 0, value, "not a positive long")

    given failFast: Transformer.Fallible[[a] =>> Either[String, a], Long, Positive] =
      create

    given accumulating: Transformer.Fallible[[a] =>> Either[List[String], a], Long, Positive] =
      create(_).left.map(_ :: Nil)
```
@:@

...and also an input value that we'll transform later down the line:

```scala mdoc:silent
val wirePerson = wire.Person(
  "John",
  "Doe",
  List(
    wire.PaymentMethod.Cash,
    wire.PaymentMethod.PayPal("john@doe.com"),
    wire.PaymentMethod.Card("J. Doe", 23232323)
  )
)
```

### Using fallible transformations

Before anything happens we've got to choose a `Mode`, i.e. a thing that dictates how the transformation gets expanded and what wrapper type will it use.
There are two flavors of `Modes`:
* `Mode.Accumulating` for error accumulation,
* `Mode.FailFast` for the cases where we just want to bail at the very first sight of trouble.

These will be used interchangably throughout the examples below, but if you want to go more in depth on those head on over to [definition of Mode](definition_of_transformer_fallible_and_mode.md)

* `Source#fallibleTo[Dest]` - for any two types `Source` and `Dest`, used to create a direct transformation between `Source` and `Dest` but taking into account all of the fallible transformations between the fields:

@:select(underlying-code-1)
@:choice(visible)
```scala mdoc
given Mode.Accumulating.Either[String, List]()

wirePerson.fallibleTo[domain.Person]
```
@:choice(generated)
```scala mdoc:passthrough
import io.github.arainko.ducktape.docs.*

Docs.printCode(wirePerson.fallibleTo[domain.Person])
``` 
@:@

Read more about the rules under which the transformations are generated in a chapter dedicated to [transformation rules](../transformation_rules.md).

* `Source#into[Dest].fallible` -  for any two types `Source` and `Dest`, used to create a 'transformation builder' that allows fixing transformation errors and overriding transformations for selected fields or subtypes.

@:select(underlying-code-2)
@:choice(visible)
```scala mdoc:nest
given Mode.FailFast.Either[String]()

wirePerson
  .into[domain.Person]
  .fallible
  .transform(
    Field.fallibleConst(
      _.paymentMethods.element.at[domain.PaymentMethod.PayPal].email,
      newtypes.NonEmptyString.create("overridden@email.com")
    )
  )
```
@:choice(generated)
```scala mdoc:passthrough
import io.github.arainko.ducktape.docs.*

Docs.printCode(
  wirePerson
    .into[domain.Person]
    .fallible
    .transform(
      Field.fallibleConst(
        _.paymentMethods.element.at[domain.PaymentMethod.PayPal].email,
        newtypes.NonEmptyString.create("overridden@email.com")
      )
    )
)
``` 
@:@

Read more in the section about [configuring fallible transformations](configuring_fallible_transformations.md).

* `Source#fallibleVia(<method reference>)` - for any type `Source` and a method reference that can be eta-expanded into a function with named arguments (which is subsequently used to expand the method's argument list with the fields of the `Source` type):

@:select(underlying-code-3)
@:choice(visible)
```scala mdoc:nest
given Mode.Accumulating.Either[String, List]()

wirePerson.fallibleVia(domain.Person.apply)
```
@:choice(generated)
```scala mdoc:passthrough
import io.github.arainko.ducktape.docs.*

Docs.printCode(wirePerson.fallibleVia(domain.Person.apply))
``` 
@:@

* `Source.intoVia(<method reference>).fallible` - for any type `Source` and a method reference that can be eta-expanded into a function with named arguments, used to create a 'transformation builder' that allows fixing transformation errors and overriding transformations for selected fields or subtypes.

@:select(underlying-code-4)
@:choice(visible)
```scala mdoc:nest
given Mode.FailFast.Either[String]()

wirePerson
  .intoVia(domain.Person.apply)
  .fallible
  .transform(
    Field.fallibleConst(
      _.paymentMethods.element.at[domain.PaymentMethod.PayPal].email,
      newtypes.NonEmptyString.create("overridden@email.com")
    )
  )
```

@:choice(generated)
```scala mdoc:passthrough
Docs.printCode(
  wirePerson
    .intoVia(domain.Person.apply)
    .fallible
    .transform(
      Field.fallibleConst(
        _.paymentMethods.element.at[domain.PaymentMethod.PayPal].email,
        newtypes.NonEmptyString.create("overridden@email.com")
      )
    )
)
``` 
@:@

Read more in the section about [configuring fallible transformations](configuring_fallible_transformations.md).
