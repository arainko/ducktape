## Basics

### Entrypoint of the library

The user-facing API of `ducktape` is mostly a bunch of extension methods that allow us to transform between types in a variety of ways, the only import needed to get started looks like this:

```scala
import io.github.arainko.ducktape.*
```

The import above brings in a number of extension methods, let's examine how these work by redefining a simplified version of the wire and domain models first seen in the [motivating example](../index.md#motivating-example):

@:select(model)
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
object domain:
  final case class Person(
    firstName: String,
    lastName: String,
    paymentMethods: Vector[domain.PaymentMethod]
  )

  enum PaymentMethod:
    case PayPal(email: String)
    case Card(digits: Long, name: String)
    case Cash
```
@:@

...and creating an input instance of `wire.Person` to be transformed into `domain.Person` later down the line:
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

### Using total transformations

* `Source#to[Dest]` - for any two types `Source` and `Dest`, used to create a direct transformation between `Source` and `Dest`:

@:select(underlying-code-1)
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

Read more about the rules under which the transformations are generated in a chapter dedicated to [transformation rules](../transformation_rules.md).

* `Source#into[Dest]` -  for any two types `Source` and `Dest`, used to create a 'transformation builder' that allows fixing transformation errors and overriding transformations for selected fields or subtypes.

@:select(underlying-code-2)
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

Read more in the section about [configuring transformations](configuring_transformations.md).

* `Source#via(<method reference>)` - for any type `Source` and a method reference that can be eta-expanded into a function with named arguments (which is subsequently used to expand the method's argument list with the fields of the `Source` type):

@:select(underlying-code-3)
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

To read about how these transformations are generated head on over to the section about [transformation rules](../transformation_rules.md).

* `Source.intoVia(<method reference>)` - for any type `Source` and a method reference that can be eta-expanded into a function with named arguments, used to create a 'transformation builder' that allows fixing transformation errors and overriding transformations for selected fields or subtypes.

@:select(underlying-code-4)
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

Read more in the section about [configuring transformations](configuring_transformations.md).
