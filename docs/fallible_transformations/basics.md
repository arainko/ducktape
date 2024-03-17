## Basics

### Entrypoint

Much as in the case of [total transformations](../total_transformations/basics.md) the entry point of fallible transformations is just single import that brings in a bunch of extension methods:

```scala mdoc
import io.github.arainko.ducktape.*
```

### Introduction

Sometimes we 

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

    given failFast: Transformer.Fallible[[a] =>> Either[String, a], String, NonEmptyString] = 
      create

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

Sometimes ordinary field mappings just do not cut it, more often than not our domain model's constructors are hidden behind a safe factory method, eg.:

```scala mdoc:reset
import io.github.arainko.ducktape.*

final case class Person private (name: String, age: Int)

object Person {
  def create(name: String, age: Int): Either[String, Person] =
    for {
      validatedName <- Either.cond(!name.isBlank, name, "Name should not be blank")
      validatedAge <- Either.cond(age > 0, age, "Age should be positive")
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

## Making the most out of `Fallible Transformers`

Now for the meat and potatoes of `Fallible Transformers`. To make use of the derivation mechanism that `ducktape` provides we should strive for our model to be modeled in a specific way - with a new nominal type per each validated field, which comes down to... Newtypes!

Let's define a minimalist newtype abstraction that will also do validation (this is a one-time effort that can easily be extracted to a library):

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
