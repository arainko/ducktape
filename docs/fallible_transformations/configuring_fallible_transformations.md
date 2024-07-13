## Configuring fallible transformations

### Prelude

If we were to dissect how the types behind config options are structured, we'd see this:

```scala
opaque type Field[A, B] <: Field.Fallible[Nothing, A, B] = Field.Fallible[Nothing, A, B]

object Field {
  opaque type Fallible[+F[+x], A, B] = Unit
}
```

Non-fallible config options are a subtype of fallible configs, which means that all the things mentioned in [`configuring transformations`](../total_transformations/configuring_transformations.md) are also applicable to fallible configurations (and should be read before diving into this doc).

Having said all that, let's declare a wire/domain model pair we'll be working on:

@:select(fallible-model)

@:choice(wire)
```scala mdoc
object wire:
  case class Person(name: String, age: Long, socialSecurityNo: String)
```

@:choice(domain)
```scala mdoc
import newtypes.*

object domain:
  case class Person(name: NonEmptyString, age: Positive, socialSecurityNo: NonEmptyString)
```
@:choice(newtypes)
```scala mdoc
import io.github.arainko.ducktape.*

object newtypes:
  opaque type NonEmptyString <: String = String

  object NonEmptyString:
    def make(value: String): Either[String, NonEmptyString] =
      Either.cond(!value.isBlank, value, s"not a non-empty string")

    def makeAccumulating(value: String): Either[List[String], NonEmptyString] =
      make(value).left.map(_ :: Nil)

    given failFast: Transformer.Fallible[[a] =>> Either[String, a], String, NonEmptyString] =
      make

    given accumulating: Transformer.Fallible[[a] =>> Either[List[String], a], String, NonEmptyString] =
      makeAccumulating

  opaque type Positive <: Long = Long

  object Positive:
    def make(value: Long): Either[String, Positive] =
      Either.cond(value > 0, value, "not a positive long")

    def makeAccumulating(value: Long): Either[List[String], Positive] =
      make(value).left.map(_ :: Nil)

    given failFast: Transformer.Fallible[[a] =>> Either[String, a], Long, Positive] =
      make

    given accumulating: Transformer.Fallible[[a] =>> Either[List[String], a], Long, Positive] =
      makeAccumulating
```
@:@

...and some input examples:

```scala mdoc:silent
// this should trip up our validation
val bad = wire.Person(name = "", age = -1, socialSecurityNo = "SOCIALNO")

// this one should pass
val good = wire.Person(name = "ValidName", age = 24, socialSecurityNo = "SOCIALNO")
```

### Product configurations

* `Field.fallibleConst` - a fallible variant of `Field.const` that allows for supplying values wrapped in an `F`

@:select(underlying-code-1)
@:choice(visible)
```scala mdoc:nest
import io.github.arainko.ducktape.*

given Mode.Accumulating.Either[String, List]()

bad
  .into[domain.Person]
  .fallible
  .transform(
    Field.fallibleConst(_.name, NonEmptyString.makeAccumulating("ConstValidName")),
    Field.fallibleConst(_.age, Positive.makeAccumulating(25))
  )
```
@:choice(generated)
```scala mdoc:passthrough
import io.github.arainko.ducktape.docs.*

Docs.printCode(
  bad
    .into[domain.Person]
    .fallible
    .transform(
      Field.fallibleConst(_.name, NonEmptyString.makeAccumulating("ConstValidName")),
      Field.fallibleConst(_.age, Positive.makeAccumulating(25))
    )
)
```
@:@

* `Field.fallibleComputed` - a fallible variant of `Field.computed` that allows for supplying functions that return values wrapped in an `F`


@:select(underlying-code-2)
@:choice(visible)
```scala mdoc:nest
given Mode.Accumulating.Either[String, List]()

bad
  .into[domain.Person]
  .fallible
  .transform(
    Field.fallibleComputed(_.name, uvp => NonEmptyString.makeAccumulating(uvp.name + "ConstValidName")),
    Field.fallibleComputed(_.age, uvp => Positive.makeAccumulating(uvp.age + 25))
  )
```
@:choice(generated)
```scala mdoc:passthrough
Docs.printCode(
  bad
    .into[domain.Person]
    .fallible
    .transform(
      Field.fallibleComputed(_.name, uvp => NonEmptyString.makeAccumulating(uvp.name + "ConstValidName")),
      Field.fallibleComputed(_.age, uvp => Positive.makeAccumulating(uvp.age + 25))
    )
)
```
@:@

### Coproduct configurations

Let's define a wire enum (pretend that it's coming from... somewhere) and a domain enum that doesn't exactly align with the wire one.
```scala mdoc:nest
object wire:
  enum ReleaseKind:
    case LP, EP, Single

object domain:
  enum ReleaseKind:
    case EP, LP
```

* `Case.fallibleConst` - a fallible variant of `Case.const` that allows for supplying values wrapped in an `F`

@:select(underlying-code-3)
@:choice(visible)
```scala mdoc:nest
given Mode.FailFast.Either[String]()

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

@:select(underlying-code-4)
@:choice(visible)
```scala mdoc:nest
given Mode.FailFast.Either[String]()

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

## Building custom instances of fallible transformers
Life is not always lolipops and crisps and sometimes you need to write a typeclass instance by hand. Worry not though, just like in the case of total transformers, we can easily define custom instances with the help of the configuration DSL (which, let's write it down once again, is a superset of total transformers' DSL).

By all means go wild with the configuration options, I'm too lazy to write them all out here again.

```scala mdoc:nest:invisible
object wire:
  case class Person(name: String, age: Long, socialSecurityNo: String)

object domain:
  case class Person(name: NonEmptyString, age: Positive, socialSecurityNo: NonEmptyString)
```

@:select(underlying-code-5)
@:choice(visible)
```scala mdoc:nest:silent
given Mode.Accumulating.Either[String, List]()

val customAccumulating =
  Transformer
    .define[wire.Person, domain.Person]
    .fallible
    .build(
      Field.fallibleConst(_.name, NonEmptyString.makeAccumulating("IAmAlwaysValidNow!"))
    )
```
@:choice(generated)
```scala mdoc:passthrough
Docs.printCode(
  Transformer
    .define[wire.Person, domain.Person]
    .fallible
    .build(
      Field.fallibleConst(_.name, NonEmptyString.makeAccumulating("IAmAlwaysValidNow!"))
    )
)
```
@:@

And for the ones that are not keen on writing out method arguments:

@:select(underlying-code-6)
@:choice(visible)
```scala mdoc:nest:silent
given Mode.Accumulating.Either[String, List]()

val customAccumulatingVia =
  Transformer
    .defineVia[wire.Person](domain.Person.apply)
    .fallible
    .build(
      Field.fallibleConst(_.name, NonEmptyString.makeAccumulating("IAmAlwaysValidNow!"))
    )
```
@:choice(generated)
```scala mdoc:passthrough
Docs.printCode(
  Transformer
    .defineVia[wire.Person](domain.Person.apply)
    .fallible
    .build(
      Field.fallibleConst(_.name, NonEmptyString.makeAccumulating("IAmAlwaysValidNow!"))
    )
)
```
@:@
