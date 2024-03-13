## Transfomation rules

Let's go over the priority and rules that `ducktape` uses to create a transformation (in the same order they're tried in the implementation):

### 1. User supplied `Transformers`

Custom instances of a `Transfomer` are always prioritized since these also function as an extension mechanism of the library.

@:select(underlying-code)
@:choice(visible)
```scala mdoc
import io.github.arainko.ducktape.*

given Transformer[String, List[String]] = str => str :: Nil

"single value".to[List[String]]
```

@:choice(generated)
```scala mdoc:passthrough
import io.github.arainko.ducktape.docs.*

Docs.printCode("single value".to[List[String]])
``` 
@:@

### 2. Upcasting

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

### 3. Mapping over an `Option`

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

### 4. Transforming and wrapping in an `Option`

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

### 5. Mapping over and changing the collection type

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

### 6. Transforming between case classes

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

### 7. Transforming between enums/sealed traits

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

### 8. Same named singletons

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

### 9. Unwrapping a value class

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

### 10. Wrapping a value class

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

### 11. Automatically derived `Transformer.Derived`

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
  def transformSource[A, B](source: Source[A])(using Transformer.Derived[A, B]): Dest[B] = source.to[Dest[B]]

  transformSource[Int, Option[Int]](Source(1, "2", 3))
}
``` 
@:@
