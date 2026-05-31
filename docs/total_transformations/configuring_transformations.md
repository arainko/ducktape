## Configuring transformations

### Introduction

More often than not the models we work with daily do not map one-to-one with one another - let's define a wire/domain model pair that we'd like to transform.

#### Case classes and enums/sealed traits

@:select(model)
@:choice(wire)
```scala mdoc
object wire:
  case class Person(
    firstName: String,
    lastName: String,
    paymentMethods: List[wire.PaymentMethod]
  )

  enum PaymentMethod:
    case Card(name: String, digits: Long)
    case PayPal(email: String)
    case Cash
    case Transfer(accountNo: String) // <-- additional enum case, not present in the domain model
```

@:choice(domain)
```scala mdoc
object domain:
  case class Person(
    firstName: String,
    lastName: String,
    age: Int, // <-- additional field, not present in the wire model
    paymentMethods: Vector[domain.PaymentMethod]
  )

  enum PaymentMethod:
    case Card(name: String, digits: Long)
    case PayPal(email: String)
    case Cash
```
@:@

...and an input value we want transformed:

```scala mdoc:silent
val wirePerson = wire.Person(
  "John",
  "Doe",
  List(
    wire.PaymentMethod.Cash,
    wire.PaymentMethod.PayPal("john@doe.com"),
    wire.PaymentMethod.Card("J. Doe", 23232323),
    wire.PaymentMethod.Transfer("21371284583271927489486")
  )
)
```

If we were to just call `.to[domain.Person]` the compiler would yell at us with a (hopefully) helpful message that should lead us into being able to complete such transformation:

```scala mdoc:fail
import io.github.arainko.ducktape.*

wirePerson.to[domain.Person]
```

The newly added field (`age`) and enum case (`PaymentMethod.Transfer`) do not have a corresponding mapping, let's say we want to set the age field to a constant value of 24 and when a `PaymentMethod.Transfer` is encountered we map it to `Cash` instead.

@:select(underlying-code-1)
@:choice(visible)

```scala mdoc
import io.github.arainko.ducktape.*

wirePerson
  .into[domain.Person]
  .transform(
    Field.const(_.age, 24),
    Case.const(_.paymentMethods.element.at[wire.PaymentMethod.Transfer], domain.PaymentMethod.Cash)
  )
```

@:choice(generated)
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
@:@

Great! But let's take a step back and examine what we just did, starting with the first config example:
```scala
Field.const(_.age, 24)
            |      |
            |      the second argument is the constant itself, whatever value is passed here needs to be a subtype of the field type 
            the first argument is the path to the field we're configuring (all Field configs operate on the destination type)        
```

...and now for the second one:

```scala
Case.const(_.paymentMethods.element.at[wire.PaymentMethod.Transfer], domain.PaymentMethod.Cash)
              |             |       |
              |             |       '.at' is another special case used to pick a subtype of an enum/sealed trait
              |             '.element' is a special extension method that allows us to configure the type inside a collection or an Option
              path expressions are not limited to a single field, we can use these to dive as deep as we need for our config to be (paths inside Case configs operate on the source type)
```

#### Tuples

Additionally, if we were to define a transformation that uses tuples and wanted to configure one of the tuple elements we can do this in two ways - either use `.apply(N)` or `._(N + 1)` (like `._1` for the 0th element of the tuple) where N is the index of the tuple element:

@:select(underlying-code-2)
@:choice(visible)
```scala mdoc
// using `.apply` to select the element
(1, List(2, 2, 2), 3, 4).into[(Int, Vector[Int], Int)].transform(Field.const(_.apply(2), 10))

// using the legacy accessors
(1, List(2, 2, 2), 3, 4).into[(Int, Vector[Int], Int)].transform(Field.const(_._3, 10))
```
@:choice(generated)
```scala mdoc:passthrough
Docs.printCode {
  val source = (1, List(2, 2, 2), 3, 4)

// using `.apply` to select the element
  source.into[(Int, Vector[Int], Int)].transform(Field.const(_.apply(2), 10))

// using the legacy accessors
  source.into[(Int, Vector[Int], Int)].transform(Field.const(_._3, 10))
}
```
@:@

For all intents and purposes these two ways of accessing tuple elements are equivalent with the exception of XXL tuples (i.e. tuples with more than 22 elements), these do not have legacy accessors and can only be configured with `.apply`.

TL;DR here's the cheat sheet of the configuration path DSL:

|   **Input type**  | **Config accessor** |
|:-----------------:|:-------------------:|
|     Case class    |      `.fieldName`     |
|   Tuple (plain)   |   `._N / .apply(N)`   |
|    Tuple (XXL)    |      `.apply(N)`      |
| Option/Collection |       `.element`      |
|  F-wrapped value  |       `.element`      |
| Enum/sealed trait |     `.at[Subtype]`    |

### Explanation

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

  extension [Elem, F[+x]](using Mode[F])(self: F[Elem]) def element: Elem
}
```

Which means that for a context function such as `Selector ?=> Dest => DestFieldTpe` the `Selector` brings in the neccessary extensions that allow us to pick and configure subtypes and elements under a collection or an `Option`(or any wrapper type `F[_]` given that it has an instance of `Mode[F]`), but only in the scope of that context function and not anywhere outside which means we do not pollute the outside world's namespace with these.

What's worth noting is that any of the configuration options are purely a compiletime construct and are completely erased from the runtime representation (i.e. it's not possible to implement an instance of a `Selector` in a sane way since such an implementation would throw exceptions left and right but using it as a sort of a DSL for picking and choosing is completely fair game since it doesn't exist at runtime).

### Product configurations

|   **Name**  | **Description** |
|:-----------------:|:-------------------:|
|     `Field.const`   |      allows to supply a constant value for a given field     |
|   `Field.computed`  |   allows to compute a value with a function that has a shape of `Dest => FieldTpe`   |
|   `Field.default`  |   only works when a field's got a default value defined (defaults are not taken into consideration by default)   |
|   `Field.computedDeep` | allows to compute a deeply nested field (for example going through multiple `Options` or other collections) |
|   `Field.allMatching`  |   allow to supply a field source whose fields will replace all matching fields in the destination (given that the names and the types match up)   |
|   `Field.fallbackToDefault`  |   falls back to default field values but ONLY in case a transformation cannot be created   |
|   `Field.fallbackToNone`  |   falls back to `None` for `Option` fields for which a transformation cannot be created  |
|   `Field.modifySourceNames`  |   allows to transform names of fields on the source type side  |
|   `Field.modifyDestNames`  |   allows to transform names of fields on the dest type side  |

---

Let's introduce another payment method (not part of any of the previous payment method ADTs, just a standalone case class).

```scala mdoc:silent
case class PaymentBand(name: String, digits: Long, color: String = "red")

val card: wire.PaymentMethod.Card =
  wire.PaymentMethod.Card(name = "J. Doe", digits = 213712345)
```

* `Field.const` - allows to supply a constant value for a given field

@:select(underlying-code-3)
@:choice(visible)
```scala mdoc
card
  .into[PaymentBand]
  .transform(Field.const(_.color, "blue"))
```
@:choice(generated)
```scala mdoc:passthrough
import io.github.arainko.ducktape.docs.*

Docs.printCode(
  card
    .into[PaymentBand]
    .transform(Field.const(_.color, "blue"))
)
``` 
@:@

* `Field.computed` - allows to compute a value with a function the shape of `Dest => FieldTpe`

@:select(underlying-code-4)
@:choice(visible)
```scala mdoc
card
  .into[PaymentBand]
  .transform(
    Field.computed(_.color, card => if card.digits % 2 == 0 then "green" else "yellow")
  )
```
@:choice(generated)
```scala mdoc:passthrough
import io.github.arainko.ducktape.docs.*

Docs.printCode(
  card
    .into[PaymentBand]
    .transform(
      Field.computed(_.color, card => if card.digits % 2 == 0 then "green" else "yellow")
    )
)
``` 
@:@

* `Field.default` - only works when a field's got a default value defined (defaults are not taken into consideration by default)

@:select(underlying-code-5)
@:choice(visible)
```scala mdoc
card
  .into[PaymentBand]
  .transform(Field.default(_.color))
```
@:choice(generated)
```scala mdoc:passthrough
import io.github.arainko.ducktape.docs.*

Docs.printCode(
  card
    .into[PaymentBand]
    .transform(Field.default(_.color))
)
``` 
@:@

* `Field.computedDeep` - allows to compute a deeply nested field (for example going through multiple `Options` or collections)

```scala mdoc:nest:silent
case class SourceToplevel1(level1: Option[SourceLevel1])
case class SourceLevel1(level2: Option[SourceLevel2])
case class SourceLevel2(int: Int)

case class DestToplevel1(level1: Option[DestLevel1])
case class DestLevel1(level2: Option[DestLevel2])
case class DestLevel2(int: Long)

val source = SourceToplevel1(Some(SourceLevel1(Some(SourceLevel2(1)))))
```

@:select(underlying-code-13)
@:choice(visible)

```scala mdoc
source
  .into[DestToplevel1]
  .transform(
    Field.computedDeep(
      _.level1.element.level2.element.int,
      // the type here cannot be inferred automatically and needs to be provided by the user,
      // a nice compiletime error message is shown (with a suggestion on what the proper type to use is) otherwise
      (value: Int) => value + 10L
    )
  )
```

@:choice(generated)
```scala mdoc:passthrough
import io.github.arainko.ducktape.docs.*

Docs.printCode(
  source
    .into[DestToplevel1]
    .transform(Field.computedDeep(_.level1.element.level2.element.int, (value: Int) => value + 10L))
)
``` 

@:@

* `Field.allMatching` - allow to supply a field source whose fields will replace all matching fields in the destination (given that the names and the types match up)

```scala mdoc:nest:silent
case class FieldSource(color: String, digits: Long, extra: Int)
val source = FieldSource("magenta", 123445678, 23)
```

@:select(underlying-code-6)
@:choice(visible)
```scala mdoc
card
  .into[PaymentBand]
  .transform(Field.allMatching(paymentBand => paymentBand, source))
```
@:choice(generated)
```scala mdoc:passthrough
import io.github.arainko.ducktape.docs.*

Docs.printCode(
  card
    .into[PaymentBand]
    .transform(Field.allMatching(paymentBand => paymentBand, source))
)
``` 
@:@

* `Field.fallbackToDefault` - falls back to default field values but ONLY in case a transformation cannot be created

```scala mdoc:nest:silent
case class SourceToplevel(level1: SourceLevel1, transformableButWithDefault: Int)
case class SourceLevel1(str: String)

case class DestToplevel(level1: DestLevel1, extra: Int = 111, transformableButWithDefault: Int = 3000)
case class DestLevel1(extra: String = "level1", str: String)

val source = SourceToplevel(SourceLevel1("str"), 400)
```

@:select(underlying-code-7)
@:choice(visible)
```scala mdoc
source
  .into[DestToplevel]
  .transform(Field.fallbackToDefault)
```

@:choice(generated)
```scala mdoc:passthrough
import io.github.arainko.ducktape.docs.*

Docs.printCode(
  source
    .into[DestToplevel]
    .transform(Field.fallbackToDefault)
)
``` 
@:@

`Field.fallbackToDefault` is a `regional` config, which means that you can control the scope where it applies:

@:select(underlying-code-8)
@:choice(visible)
```scala mdoc
source
  .into[DestToplevel]
  .transform(
    Field.fallbackToDefault.regional(
      _.level1
    ), // <-- we're applying the config starting on the `.level1` field and below, it'll be also applied to other transformations nested inside
    Field.const(_.extra, 123) // <-- note that this field now needs to be configured manually
  )
```
@:choice(generated)
```scala mdoc:passthrough
import io.github.arainko.ducktape.docs.*

Docs.printCode(
  source
    .into[DestToplevel]
    .transform(
      Field.fallbackToDefault.regional(
        _.level1
      ), // <-- we're applying the config starting on the `.level1` field and below, it'll be also applied to other transformations nested inside
      Field.const(_.extra, 123)
    )
)
```
@:@

* `Field.fallbackToNone` - falls back to `None` for `Option` fields for which a transformation cannot be created

```scala mdoc:nest:silent
case class SourceToplevel(level1: SourceLevel1, transformable: Option[Int])
case class SourceLevel1(str: String)

case class DestToplevel(level1: DestLevel1, extra: Option[Int], transformable: Option[Int])
case class DestLevel1(extra: Option[String], str: String)

val source = SourceToplevel(SourceLevel1("str"), Some(400))
```

@:select(underlying-code-9)
@:choice(visible)
```scala mdoc
source
  .into[DestToplevel]
  .transform(Field.fallbackToNone)
```

@:choice(generated)
```scala mdoc:passthrough
import io.github.arainko.ducktape.docs.*

Docs.printCode(
  source
    .into[DestToplevel]
    .transform(Field.fallbackToNone)
)
``` 
@:@

`Field.fallbackToNone` is a `regional` config, which means that you can control the scope where it applies:

@:select(underlying-code-10)
@:choice(visible)
```scala mdoc
source
  .into[DestToplevel]
  .transform(
    Field.fallbackToNone.regional(
      _.level1
    ), // <-- we're applying the config starting on the `.level1` field and below, it'll be also applied to other transformations nested inside
    Field.const(_.extra, Some(123)) // <-- note that this field now needs to be configured manually
  )
```
@:choice(generated)
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
@:@

### Coproduct configurations

|   **Name**  | **Description** |
|:-----------------:|:-------------------:|
|     `Case.const`   |      allows to supply a constant value for a given subtype of a coproduct     |
|     `Case.computed`   |      allows to supply a function of the selected source type to the expected destination type    |
|     `Case.modifySourceNames`   |      allows to transform names of enum/sealed trait children and case objects of the Source type  |
|     `Case.modifyDestNames`   |      allows to transform names of enum/sealed trait children and case objects of the Dest type  |

---

```scala mdoc
val transfer = wire.PaymentMethod.Transfer("2764262")
```

* `Case.const` - allows to supply a constant value for a given subtype of a coproduct

@:select(underlying-code-11)
@:choice(visible)
```scala mdoc
transfer
  .into[domain.PaymentMethod]
  .transform(Case.const(_.at[wire.PaymentMethod.Transfer], domain.PaymentMethod.Cash))
```

@:choice(generated)
```scala mdoc:passthrough
import io.github.arainko.ducktape.docs.*

Docs.printCode(
  transfer
    .into[domain.PaymentMethod]
    .transform(Case.const(_.at[wire.PaymentMethod.Transfer], domain.PaymentMethod.Cash))
)
``` 
@:@

* `Case.computed` - allows to supply a function of the selected source type to the expected destination type

@:select(underlying-code-12)
@:choice(visible)
```scala mdoc
transfer
  .into[domain.PaymentMethod]
  .transform(
    Case.computed(_.at[wire.PaymentMethod.Transfer], transfer => domain.PaymentMethod.Card("J. Doe", transfer.accountNo.toLong))
  )
```
@:choice(generated)
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
@:@

### Field/case name transformations

#### Changing field names

Let's establish a pair of case classes with a peculiar naming scheme first:
```scala mdoc:nest:silent
case class Source(int: Int, str: String)
case class Dest(INT: Int, STR: String)

val source = Source(1, "1")
```

Obviously, we wouldn't be able to map between those two case classes since their names do not match.
To make this work we can make use of one of the following configuration options:

* `Field.modifySourceNames` - modifies source field names according to the provided `Renamer`:

@:select(underlying-code-13)
@:choice(visible)
```scala mdoc
source
  .into[Dest]
  .transform(
    Field.modifySourceNames(_.toUpperCase)
  )
```
@:choice(generated)
```scala mdoc:passthrough
import io.github.arainko.ducktape.docs.*

Docs.printCode(
  source
    .into[Dest]
    .transform(
      Field.modifySourceNames(_.toUpperCase)
    )
)
``` 
@:@

* `Field.modifyDestNames` - modifies destination field names according to the provided `Renamer`:

@:select(underlying-code-14)
@:choice(visible)
```scala mdoc
source
  .into[Dest]
  .transform(
    Field.modifyDestNames(_.toLowerCase)
  )
```
@:choice(generated)
```scala mdoc:passthrough
import io.github.arainko.ducktape.docs.*

Docs.printCode(
  source
    .into[Dest]
    .transform(
      Field.modifyDestNames(_.toLowerCase)
    )
)
``` 
@:@


#### Changing case names

Names of sealed traits, enums and case objects can also be changed, let's look at an example:

```scala mdoc:nest:silent
enum Source {
  case One, Two, Three
}

enum Dest {
  case ONE, TWO, THREE
}
```

* `Case.modifyDestNames` - modifies destination case names according to the provided `Renamer`:

@:select(underlying-code-15)
@:choice(visible)
```scala mdoc
Source.One
  .into[Dest]
  .transform(
    Case.modifyDestNames(_.toLowerCase.capitalize)
  )
```
@:choice(generated)
```scala mdoc:passthrough
import io.github.arainko.ducktape.docs.*

Docs.printCode(
  Source.One
    .into[Dest]
    .transform(
      Case.modifyDestNames(_.toLowerCase.capitalize)
    )
)
``` 
@:@

* `Case.modifySourceNames` - modifies source case names according to the provided `Renamer`:

@:select(underlying-code-16)
@:choice(visible)
```scala mdoc
Source.One
  .into[Dest]
  .transform(
    Case.modifySourceNames(_.toUpperCase)
  )
```
@:choice(generated)
```scala mdoc:passthrough
import io.github.arainko.ducktape.docs.*

Docs.printCode(
  Source.One
    .into[Dest]
    .transform(
      Case.modifySourceNames(_.toUpperCase)
    )
)
``` 
@:@

#### Constraining the blast radius of name transformations

Let's take one of the config options that modifies names (`Field.modifyDestNames`) under closer inspection:
```scala
def modifyDestNames[Source, Dest](renamer: Renamer => Renamer): Field[Source, Dest] & Regional[Dest] & Local[Dest] & TypeSpecific
```

We can see that, apart from being a `Field[Source, Dest]` (a type for describing field configs), it is also a `Regional[Dest] & Local[Dest] & TypeSpecific` - these are 'config modifiers' and each one of them introduces a method on the config option. Let's take a closer look at all of them:

* `.regional` - constrains a config option to a certain region (i.e. the option will apply to the transformations 'underneath' the selected field/case):

```scala mdoc:nest:silent
case class Source(int: Int, str: String, level1: SourceLevel1)
case class SourceLevel1(INT: Int, STR: String, LEVEL2: SourceLevel2)
case class SourceLevel2(INT: Int, STR: String)

case class Dest(int: Int, str: String, level1: DestLevel1)
case class DestLevel1(int: Int, str: String, level2: DestLevel2)
case class DestLevel2(int: Int, str: String)

val source = Source(1, "1", SourceLevel1(2, "2", SourceLevel2(3, "3")))
```

@:select(underlying-code-17)
@:choice(visible)
```scala mdoc  
source
  .into[Dest]
  .transform(
    Field.modifyDestNames(_.toUpperCase).regional(_.level1)
  ) // <-- we use `.regional` to modify all fields BELOW `Dest.level1`
```
@:choice(generated)
```scala mdoc:passthrough
import io.github.arainko.ducktape.docs.*

Docs.printCode(
  source
    .into[Dest]
    .transform(Field.modifyDestNames(_.toUpperCase).regional(_.level1))
)
``` 
@:@

If we were to use this modifier on a `Case` rename, we'd modify all case objects and the type names of all children of enums and sealed traits 'under' a type we choose.

* `.local` - constrains a config option to a certain local region (i.e. to a case class/children of an enum 'underneath' the selected field/case):

```scala mdoc:nest:silent
case class Source(int: Int, str: String, level1: SourceLevel1)
case class SourceLevel1(INT: Int, STR: String, LEVEL2: SourceLevel2)
case class SourceLevel2(int: Int, str: String)

case class Dest(int: Int, str: String, level1: DestLevel1)
case class DestLevel1(int: Int, str: String, level2: DestLevel2)
case class DestLevel2(int: Int, str: String)

val source = Source(1, "1", SourceLevel1(2, "2", SourceLevel2(3, "3")))
```

@:select(underlying-code-18)
@:choice(visible)
```scala mdoc  
source
  .into[Dest]
  .transform(
    Field.modifyDestNames(_.toUpperCase).local(_.level1)
  ) // <-- we use `.local` to only modify names under `Dest.level1` and not anywhere else
```
@:choice(generated)
```scala mdoc:passthrough
import io.github.arainko.ducktape.docs.*

Docs.printCode(
  source
    .into[Dest]
    .transform(Field.modifyDestNames(_.toUpperCase).local(_.level1))
)
``` 
@:@

If we were to use this modifier on a `Case` rename it'd bubble down (is that a phrase?) to all subtypes of an enum/sealed trait we choose, but only in that specific case. 

* `.typeSpecific` - constrains a config option to subtypes of the selected type:

```scala mdoc:nest:silent
case class Source(int: Int, str: String, level1: SourceLevel1)
case class SourceLevel1(INT: Int, STR: String, LEVEL2: SourceLevel2)
case class SourceLevel2(int: Int, str: String)

case class Dest(int: Int, str: String, level1: DestLevel1)
case class DestLevel1(
  int: Int,
  str: String,
  level2: DestLevel2
) // <-- fields of this type (and only this type) need to be uppercase
case class DestLevel2(int: Int, str: String)

val source = Source(1, "1", SourceLevel1(2, "2", SourceLevel2(3, "3")))
```

@:select(underlying-code-19)
@:choice(visible)
```scala mdoc  
source
  .into[Dest]
  .transform(
    Field.modifyDestNames(_.toUpperCase).typeSpecific[DestLevel1]
  ) // <-- we use `.typeSpecifc` to only modify names for `DestLevel1` and not anywhere else
```
@:choice(generated)
```scala mdoc:passthrough
import io.github.arainko.ducktape.docs.*

Docs.printCode(
  source
    .into[Dest]
    .transform(Field.modifyDestNames(_.toUpperCase).typeSpecific[DestLevel1])
)
``` 
@:@

#### Predefined Renamers

The companion object of `Renamer` has a bunch of common definitions for transforming between the various field encodings (snake case, camel case, kebab case), for example:

```scala mdoc:nest:silent
case class Camel(someField: Int)
case class Snake(some_field: Int)
case class Kebab(`some-field`: Int)

val camel = Camel(23)
val snake = Snake(23)
val kebab = Kebab(23)
```

@:select(underlying-code-20)
@:choice(visible)
```scala mdoc
val camelToSnake = camel.into[Snake].transform(Field.modifySourceNames(Renamer.camelCase.toSnakeCase))
val camelToKebab = camel.into[Kebab].transform(Field.modifySourceNames(Renamer.camelCase.toKebabCase))
val snakeToCamel = snake.into[Camel].transform(Field.modifySourceNames(Renamer.snakeCase.toCamelCase))
val kebabToCamel = kebab.into[Camel].transform(Field.modifySourceNames(Renamer.kebabCase.toCamelCase))
```

@:choice(generated)
```scala mdoc:passthrough
import io.github.arainko.ducktape.docs.*

Docs.printCode {
  camel.into[Snake].transform(Field.modifySourceNames(Renamer.camelCase.toSnakeCase))

  camel.into[Kebab].transform(Field.modifySourceNames(Renamer.camelCase.toKebabCase))

  snake.into[Camel].transform(Field.modifySourceNames(Renamer.snakeCase.toCamelCase))

  kebab.into[Camel].transform(Field.modifySourceNames(Renamer.kebabCase.toCamelCase))
}
``` 
@:@

#### Supported name transformations

Name transformations are designed to look like operations on your run of the mill `String`, this however is not the full story.
One of the constrains of that design is that all of the operations (and their arguments) need to be known at compiletime so that the library can actually
lift that into an actual `String => String` function to apply on the field/case names.

This leads us to `Renamer` - a small DSL that describes the supported subset of `String` methods, defined as such:
```scala
sealed trait Renamer {
  // ...small excerpt of the actual Renamer...

  /**
   * Equivalent to `String#toLowerCase`
   */
  def toLowerCase: Renamer

  /**
   * Equivalent to the function `(str: String) => Pattern.compile(pattern).matcher(str).replaceAll(replacement)`
   */
  def regexReplace(pattern: String, replacement: String): Renamer

  /**
   * Equivalent to `String#replace(target, replacement)`
   */
  def replace(target: String, replacement: String): Renamer

  // ...small excerpt of the actual Renamer...
}
```

To reiterate, all of the calls on a `Renamer` (which always takes shape of a `Renamer => Renamer` in a config option), i.e. stuff like:
```scala
Field.modifyDestNames(_.toLowerCase.replace("some_string", "with_something_else").regexReplace("""_(\d)_""", "$1"))
```
...is legal, but the moment we introduce something that ISN'T immediately known at compiletime we'll get a compilation error, for example:
```scala
Field.modifyDestNames(_.toLowerCase.replace("some_string", Random.nextString(10)))
// error: Invalid renamer expression - make sure all of the renamer expressions can be read at compiletime
```

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
    Field.const(
      _.paymentMethods,
      Vector.empty[domain.PaymentMethod]
    ) // <-- also override the 'parent' of '_.paymentMethods.element' so now payment methods are just empty
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
    Field.const(_.paymentMethods.element, domain.PaymentMethod.Cash) // <-- then the field below it
  )
```
