
## Making the most out of fallible transformations

Now for the meat and potatoes of fallible transformations. To make use of the derivation mechanism that `ducktape` provides we should strive for our model to be modeled in a specific way - with a new nominal type per each validated field, which comes down to... Newtypes!

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

Fallible transformations wrapped in some type `F` are derived automatically for case classes given that a `Transformer.Fallible` instance exists for `F` and all of the fields of the source type have a corresponding counterpart in the destination type and each one of them has an instance of either `Transformer.Fallible` or a total `Transformer` in scope.

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
