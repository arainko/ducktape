TODO CHANGE THE START OF THIS SECTION SO IT'S NOT SO RANDOM

```scala mdoc
import io.github.arainko.ducktape.*

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
case class UnvalidatedPerson(name: String, age: Int, socialSecurityNo: String)

// this should trip up our validation
val bad = UnvalidatedPerson(name = "", age = -1, socialSecurityNo = "SOCIALNO")

// this one should pass
val good = UnvalidatedPerson(name = "ValidName", age = 24, socialSecurityNo = "SOCIALNO")
```

## Configuring fallible transformations

If we were to dissect how the types behind config options are structured, we'd see this:

```scala
opaque type Field[A, B] <: Field.Fallible[Nothing, A, B] = Field.Fallible[Nothing, A, B]

object Field {
  opaque type Fallible[+F[+x], A, B] = Unit
}
```

Non-fallible config options are a subtype of fallible configs, i.e. all the things mentioned in [`configuring transformations`](../total_transformations/configuring_transformations.md) are also applicable to fallible configurations.

### Product configurations

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
import io.github.arainko.ducktape.docs.*

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

### Coproduct configurations

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

## Building custom instances of fallible transformers
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
