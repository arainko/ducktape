## Basics
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
