## Transfomation rules

Let's go over the priority and rules that `ducktape` uses to create a transformation (in the same order they're tried in the implementation):

### 1. User supplied `Transformers`

Custom instances of a `Transfomer` are always prioritized since these also function as an extension mechanism of the library.

@:select(underlying-code-1)
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

@:select(underlying-code-2)
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

### 3. Flatmapping over an arbitrary `F[_]` with a fallible transformation underneath (fallible transformations only)

A value wrapped in an arbitrary `F[_]` can be flatmapped over given that:
* an instance of `Mode.FailFast[F]` is in scope,
* a fallible transformation can be derived for the type being wrapped

@:select(underlying-code-3)
@:choice(visible)
```scala mdoc

case class Positive private (value: Int)

object Positive {
  given Transformer.Fallible[[a] =>> Either[String, a], Int, Positive] = 
    int => if (int < 0) Left("Lesser or equal to 0") else Right(Positive(int))
}

Mode.FailFast.either[String].locally {
  Right(1).fallibleTo[Positive]
}
```
@:choice(generated)
```scala mdoc:passthrough
Docs.printCode(
  Mode.FailFast.either[String].locally {
    Right(1).fallibleTo[Positive]
  }
)
``` 
@:@

### 4. Mapping over an arbitrary `F[_]` (fallible transformations only)

A value wrapped in an arbitrary `F[_]` can be mapped over given that:
* an instance of `Mode[F]` is in scope,
* a transformation can be derived for the type being wrapped

@:select(underlying-code-4)
@:choice(visible)
```scala mdoc

Mode.FailFast.either[String].locally {
  Right(1).fallibleTo[Option[Int]]
}
```
@:choice(generated)
```scala mdoc:passthrough
Docs.printCode(
  Mode.FailFast.either[String].locally {
    Right(1).fallibleTo[Option[Int]]
  }
)
``` 
@:@


### 5. Mapping over an `Option`

Transforming between options comes down to mapping over it and recursively deriving a transformation for the value inside.

@:select(underlying-code-5)
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

### 6. Transforming and wrapping in an `Option`

If a transformation between two types is possible then transforming between the source type and an `Option` of the destination type is just wrapping the transformation result in a `Some`.

@:select(underlying-code-6)
@:choice(visible)
```scala mdoc
1.to[Option[Int | String]]
```
@:choice(generated)
```scala mdoc:passthrough
Docs.printCode(1.to[Option[Int | String]])
``` 
@:@

### 7. Mapping over and changing the collection type

@:select(underlying-code-7)
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

### 8. Transforming between case classes

A source case class can be transformed into the destination case class given that:
* source has fields whose names cover all of the destination's fields,
* a transformation for the types corresponding to those fields can be derived.

@:select(underlying-code-8)
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

### 9. Transforming between case classes and tuples

A source case class can be transformed into a tuple given that:
* the order of its fields align with the tuple's elements (the source's length CAN be greater than the destination tuple's length),
* a transformation for the types corresponding to the aligned fields and elements can be derived

@:select(underlying-code-9)
@:choice(visible)
```scala mdoc:reset-object
import io.github.arainko.ducktape.*

case class Source(field1: Int, field2: List[Int], field3: Int, field4: Int)

Source(1, List(2, 2, 2), 3, 4).to[(Int, Vector[Int], Option[Int])]
```

@:choice(generated)
```scala mdoc:passthrough
import io.github.arainko.ducktape.docs.*

Docs.printCode(
  Source(1, List(2, 2, 2), 3, 4).to[(Int | String, Vector[Int], Option[Int])]
)
```
@:@

### 10. Transforming between tuples and case classes

A source tuple can be transformed into a case class given that:
* the order of its elements align with the case class' fields (the source's length CAN be greater than the destination's length),
* a transformation for the types corresponding to the aligned fields and elements can be derived

@:select(underlying-code-10)
@:choice(visible)
```scala mdoc:reset-object
import io.github.arainko.ducktape.*

case class Dest(field1: Int, field2: List[Int], field3: Option[Int])

(1, Vector(2, 2, 2), 3, 4).to[Dest]
```

@:choice(generated)
```scala mdoc:passthrough
import io.github.arainko.ducktape.docs.*

Docs.printCode(
  (1, Vector(2, 2, 2), 3, 4).to[Dest]
)
```
@:@

### 11. Transforming between tuples

A source tuple can be transformed into a destination tuple given that:
* the order of its elements align with the destination tuple's elements (the source's length CAN be greater than the destination's length),
* a transformation for the types corresponding to the aligned fields and elements can be derived

@:select(underlying-code-11)
@:choice(visible)
```scala mdoc:reset-object
import io.github.arainko.ducktape.*

(1, Vector(2, 2, 2), 3, 4).to[(Int, List[Int], Option[Int])]
```

@:choice(generated)
```scala mdoc:passthrough
import io.github.arainko.ducktape.docs.*

Docs.printCode(
  (1, Vector(2, 2, 2), 3, 4).to[(Int, List[Int], Option[Int])]
)
```
@:@

### 12. Transforming between enums/sealed traits

A source coproduct can be transformed into the destination coproduct given that:
* destination's children have names that match all of the source's children,
* a transformation between those two corresponding types can be derived.

@:select(underlying-code-12)
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

### 13. Same named singletons

Transformations between same named singletons come down to just reffering to the destination singleton.

@:select(underlying-code-13)
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

### 14. Unwrapping a value class

@:select(underlying-code-14)
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

### 15. Wrapping a value class

@:select(underlying-code-15)
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

### 16. Automatically derived `Transformer.Derived`

Instances of `Transformer.Derived` are automatically derived as a fallback to support use cases where a generic type (eg. a field of a case class) is unknown at definition site.

Note that `Transformer[A, B] <: Transformer.Derived[A, B]` so any `Transformer` in scope is eligible to become a `Transformer.Derived`.

@:select(underlying-code-16)
@:choice(visible)
```scala mdoc:reset-object
import io.github.arainko.ducktape.*

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
