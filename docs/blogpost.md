# Is ducktape still all duct tape under the hood; or, why are macros so cool that I'm basically rewriting it for the third time?

### Prelude

Before I go off talking about the insides of the library, let's first touch base on what `ducktape` actually is, [its Github page](https://github.com/arainko/ducktape/tree/series/0.2.x) describes it as this:

```
Automatic and customizable compile time transformations between similar case classes and sealed traits/enums, essentially a thing that glues your code.
```

The last part of that sentence also shines some light on why the name is what it is, it was originally supposed to be called `ducttape` to signify its potential of generating *glue* code for you. 

However, the idea of joining two words that end and start with the same letter wouldn't let me sleep, so I went with what I considered an absolute kino of a name at the time - `ducktape`, but I digress.

![ducktape-logo-32](https://user-images.githubusercontent.com/46346508/236060869-3b118075-f660-44c9-9d0d-d40fba5c8db0.svg)

### Motivating example

For the purposes of showing what the library is capable of, let's consider two nearly identical models, a `wire` model:

```scala mdoc:silent
import io.github.arainko.ducktape.*
import java.time.Instant

object wire {
  final case class Person(
    firstName: String,
    lastName: String,
    paymentMethods: List[wire.PaymentMethod],
    status: wire.Status,
    updatedAt: Option[Instant],
  )

  enum Status:
    case Registered, PendingRegistration, Removed

  enum PaymentMethod:
    case Card(name: String, digits: Long, expires: Instant)
    case PayPal(email: String)
    case Cash
}
```
...and a `domain` model:
```scala mdoc:silent
object domain {
  final case class Person( // <-- fields reshuffled 
    lastName: String,
    firstName: String,
    status: Option[domain.Status], // <-- 'status' in the domain model is optional
    paymentMethods: Vector[domain.Payment], // <-- collection type changed from a List to a Vector
    updatedAt: Option[Instant],
  )

  enum Status:
    case Registered, PendingRegistration, Removed
    case PendingRemoval // <-- additional enum case

  enum Payment:
    case Card(name: String, digits: Long, expires: Instant)
    case PayPal(email: String)
    case Cash
}
```

So, we can imagine having to somehow map between these two since the `wire` model is something that our HTTP spits out, eg. given a `wire.Person` defined as such:
```scala mdoc:silent
val wirePerson: wire.Person = wire.Person(
  "John",
  "Doe",
  List(
    wire.PaymentMethod.Cash,
    wire.PaymentMethod.PayPal("john@doe.com"),
    wire.PaymentMethod.Card("J. Doe", 12345, Instant.now)
  ),
  wire.Status.PendingRegistration,
  Some(Instant.ofEpochSecond(0))
)
```

We can turn it into a `domain.Person` in a single, yet wonderful, line of code:

```scala mdoc
val domainPerson = wirePerson.to[domain.Person]
```

<details>
  <summary>Can you imagine writing all that by hand?</summary>
  
```scala mdoc:passthrough
  import io.github.arainko.ducktape.docs.*

  Docs.printCode(
    wirePerson.to[domain.Person]
  )
``` 

I'd rather stare at a CI pipeline that fails after 18 minutes because I forgot to format my code than write this piece of code by hand.
</details>


### Previous 'art'

As hinted in the overlong title, which is itself inspired by the title of this album (go listen to it, it's got a cool lizard on the cover)...
<iframe style="border: 0; width: 100%; height: 120px;" src="https://bandcamp.com/EmbeddedPlayer/album=3699259806/size=large/bgcol=ffffff/linkcol=0687f5/tracklist=false/artwork=small/transparent=true/" seamless><a href="https://kinggizzard.bandcamp.com/album/petrodragonic-apocalypse-or-dawn-of-eternal-night-an-annihilation-of-planet-earth-and-the-beginning-of-merciless-damnation">PetroDragonic Apocalypse;  or,  Dawn of Eternal Night:  An Annihilation of Planet Earth and the Beginning of Merciless Damnation by King Gizzard &amp; The Lizard Wizard</a></iframe>

...the library has been through 2 phases already.

The first of which is documented in [this blogpost](https://scalac.io/blog/inline-your-boilerplate-harnessing-scala3-metaprogramming-without-macros/) and can be summarized as match type abuse (the `0.0.x` line of releases).

The second one being the `0.1.x` line of releases which pretty much scrapped all traces of its predecessor and replaced it with those pesky macros and developed an overreliance on automatic typeclass derivation which then had to be unpacked in a process I can only call ['beta-reduction at home'](https://github.com/arainko/ducktape/blob/5d266a7c9076037e6512c0a740b2065e4f077828/ducktape/src/main/scala/io/github/arainko/ducktape/internal/macros/LiftTransformation.scala#L113)  to not generate unnecessary `Transformer` (the typeclass being automatically derived) instances at runtime. All in all, a pretty fun piece of code.

The third and newest iteration is the `0.2.x` line or releases (sitting at a [Milestone 2](https://github.com/arainko/ducktape/releases/tag/v0.2.0-M2) release at the time of writing) - this time I took a more thought-through approach to structuring the library than constantly telling the compiler to derive that good good.

The main motivation was being able to support stuff like nested configuration of fields and cases (which IMO were the worst offenders to usability of the library itself since even if your transformation was aaaaalmost there but a single field was missing in a nested case class you were done for and had to create a new `Transformer` instance and put it in implicit scope) and being able to show the user all of the failures that occurred all at once in addition to being more actionable than just `Yeah, the field 'chips' is missing in Diner`.

### Reifying all the stuff

Most of the issues of `0.1.x` came from relying on automatic derivation of `Transformers` to do basically everything, which resulted in the library not really being in control of anything since it gave away control to the compiler right after pulling out of the driveway, so to be able to do all of the things listed above I had to find a way of introspecting and somehow transforming the transformations, which basically came down to data-fying each and every step. 
Let's take a look at a highlevel overview of the new architecture:

<pre class="mermaid">
  flowchart TD
    Input1["Type.of[Source]"] -->|Structure.of| SourceStruct("Structure[Source]") 
    Input2["Type.of[Dest]"] -->|Structure.of| DestStruct("Structure[Dest]")
    InputExpr["Expr[Source]"]
    SourceStruct -->|Planner.create| InitialPlan("Plan[Plan.Error]")
    DestStruct -->|Planner.create| InitialPlan
    InitialPlan -->|PlanRefiner.run| RefinedPlan("Either[NonEmptyList[Plan.Error], Plan[Nothing]]")
    RefinedPlan -->|Transformations.createTransformation| NonErroneousPlan("Plan[Nothing]")
    NonErroneousPlan -->|PlanInterpreter.run| ExpressionOfDest("Expr[Dest]")
    InputExpr -->|PlanInterpreter.run| ExpressionOfDest
</pre>

<script type="module">
  import mermaid from 'https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.esm.min.mjs';
</script>

So, what is all this stuff you may ask and am I even showing you this... Let's try to peel it back layer by layer starting from the topmost piece of the graph, i.e:

<pre class="mermaid">
  flowchart TD
    Input1["Type.of[Source]"] -->|Structure.of| SourceStruct("Structure[Source]") 
    Input2["Type.of[Dest]"] -->|Structure.of| DestStruct("Structure[Dest]")
    SourceStruct -->|Planner.create| InitialPlan("Plan[Plan.Error]")
    DestStruct -->|Planner.create| InitialPlan
</pre>

### The structure of a `Structure`

A `Structure` is meant to capture stuff like fields of a case class (their names and `Structures` that correspond to them), children of an enum/sealed trait and some more specialized stuff like `Optional` types, `Collections`, `Value Classes`, `Singletons` etc. ([the implementation itself looks like this](https://github.com/arainko/ducktape/blob/1cdf94d497f071a4f42269a80a2bf999eb27815b/ducktape/src/main/scala/io/github/arainko/ducktape/internal/Structure.scala)), so going back to our [Motivating Example](#motivating-example), a `Structure` of `wire.Person` looks like this:

```scala mdoc:passthrough
import io.github.arainko.ducktape.docs.*

Docs.printStructure[wire.Person]
```
So uhhh, that doesn't tell us everything since most of that stuff is `Lazy`, but it's lazy for a reason not because it doesn't want to do anything, that reason being recursive types which are suspiciously prevalent in our day to day life, looking no further than Scala's `List`.   
See, if it wasn't for those `Lazy` nodes we'd be sending the compiler on a trip it wouldn't be coming back from every time we encounter a recursive type, but if we encode the notion of laziness we can expand those calls later on when we'll be taking special care to not overflow the stack.   
You might be asking yourself, how is a `Structure` actually constructed - the answer to that, and most other things, is a big pattern match!

<details>
  <summary>click here here to see it in all of its glory</summary>

```scala
object Structure {
  def of[A: Type](path: Path)(using Quotes): Structure = {
    import quotes.reflect.*

    Type.of[A] match {
      case tpe @ '[Nothing] =>
        Structure.Ordinary(tpe, path)

      case tpe @ '[Option[param]] =>
        Structure.Optional(tpe, path, Structure.of[param](path.appended(Path.Segment.Element(Type.of[param]))))

      case tpe @ '[Iterable[param]] =>
        Structure.Collection(tpe, path, Structure.of[param](path.appended(Path.Segment.Element(Type.of[param]))))

      case tpe @ '[AnyVal] if tpe.repr.typeSymbol.flags.is(Flags.Case) =>
        val repr = tpe.repr
        val param = repr.typeSymbol.caseFields.head
        val paramTpe = repr.memberType(param)
        Structure.ValueClass(tpe, path, paramTpe.asType, param.name)

      case _ =>
        Expr.summon[Mirror.Of[A]] match {
          case None =>
            Structure.Ordinary(Type.of[A], path)

          case Some(value) =>
            value match {
              case '{
                    type label <: String
                    $m: Mirror.Singleton {
                      type MirroredLabel = `label`
                    }
                  } =>
                val value = materializeSingleton[A]
                Structure.Singleton(Type.of[A], path, constantString[label], value.asExpr)
              case '{
                    type label <: String
                    $m: Mirror.SingletonProxy {
                      type MirroredLabel = `label`
                    }
                  } =>
                val value = materializeSingleton[A]
                Structure.Singleton(Type.of[A], path, constantString[label], value.asExpr)
              case '{
                    $m: Mirror.Product {
                      type MirroredElemLabels = labels
                      type MirroredElemTypes = types
                    }
                  } =>
                val structures =
                  tupleTypeElements(TypeRepr.of[types])
                    .zip(constStringTuple(TypeRepr.of[labels]))
                    .map((tpe, name) =>
                      name -> (tpe.asType match {
                        case '[tpe] => Lazy.of[tpe](path.appended(Path.Segment.Field(Type.of[tpe], name)))
                      })
                    )
                    .toMap
                Structure.Product(Type.of[A], path, structures)
              case '{
                    $m: Mirror.Sum {
                      type MirroredElemLabels = labels
                      type MirroredElemTypes = types
                    }
                  } =>
                val structures =
                  tupleTypeElements(TypeRepr.of[types])
                    .zip(constStringTuple(TypeRepr.of[labels]))
                    .map((tpe, name) =>
                      name -> (tpe.asType match { case '[tpe] => Lazy.of[tpe](path.appended(Path.Segment.Case(Type.of[tpe]))) })
                    )
                    .toMap

                Structure.Coproduct(Type.of[A], path, structures)
            }
        }
    }
  }
}
```

[Here's a link to the full implementation](https://github.com/arainko/ducktape/blob/6698dc187775e0a55c815212f228e518cf9cd749/ducktape/src/main/scala/io/github/arainko/ducktape/internal/Structure.scala#L82)
</details>

In short, it matches on a type given to it in the type parameter of `Structure.of[A]`, gets the first few special cases out of the way:
* `Nothing` since it's a subtype of all other types,
* `Option[param]`,
* `Iterable[param]`,
* `AnyVal` that's also a case class (also known as a value class)

and then proceeds to roll up its sleeves by trying to summon an instance of a `Mirror` and matches on its subtypes:
* `Mirror.Singleton` for Scala 3 singletons,
* `Mirror.SingletonProxy` for Scala 2 singletons,
* `Mirror.Product` for case classes,
* `Mirror.Sum` for sealed traits/enums

to recursively derive `Structures` for fields/known subtypes. This gives us just enough information to be able to collate two `Structures` together in order to create a transformation plan.

### The makings of a `Plan`

When I said that it's just enough information to create a transformation plan I meant that literally, the next step in the diagram is creating a `Plan[Plan.Error]`.    
Before diving into it let's first see how does the final product look like for a transfomation between `wire.PaymentMethod` and `domain.Payment`:

```scala mdoc:passthrough
  import io.github.arainko.ducktape.docs.*

  Docs.printPlan[wire.PaymentMethod, domain.Payment]
``` 

The above can be roughly read as 'this a transformation between coproducts (source being `wire.PaymentMethod` and the destination `domain.Payment`), for which the transformations between the cases of that coproduct is as follows: 
  * `wire.PaymentMethod.Card` maps to `domain.Payment.Card` which itself is a product transformation for fields:
    * "name" which is just an upcast from the source field,
    * "digits" which is just an upcast from the source field,
    * "expires" which is just an upcast from the source field
  * `wire.PaymentMethod.PayPal` maps to `domain.Payment.PayPal` which itself is a product transformation for fields:
    * "name" which is just an upcast from the source field
  * `wire.PaymentMethod.Cash` maps to `domain.Payment.Cash` which is a singleton transformation (i.e. the value of the destination singleton is just inserted)'.

`Plans` are meant to represent a higher level representation of a transformation specifically tailored to be modifiable in a variety of ways, eg. by changing one of the nodes to a constant value. Its declaration roughly cut down to only nodes visible in the example looks like this ([for the real deal take a look here](https://github.com/arainko/ducktape/blob/6698dc187775e0a55c815212f228e518cf9cd749/ducktape/src/main/scala/io/github/arainko/ducktape/internal/Plan.scala#L13)):

```scala
sealed trait Plan[+E <: Plan.Error]

object Plan {
  case class Upcast(
    source: Structure,
    dest: Structure
  ) extends Plan[Nothing]

  case class BetweenSingletons(
    source: Structure.Singleton,
    dest: Structure.Singleton
  ) extends Plan[Nothing]

  case class BetweenProducts[+E <: Plan.Error](
    source: Structure.Product,
    dest: Structure.Product,
    fieldPlans: Map[String, Plan[E]]
  ) extends Plan[E]

  case class BetweenCoproducts[+E <: Plan.Error](
    source: Structure.Coproduct,
    dest: Structure.Coproduct,
    casePlans: Vector[Plan[E]]
  ) extends Plan[E]

  case class Error(
    source: Structure,
    dest: Structure,
    message: ErrorMessage,
    suppressed: Option[Plan.Error]
  ) extends Plan[Plan.Error]

  //... more cases elided for readability
}
```
There isn't that much going on here, besides that weird `+E <: Plan.Error` - why is it there exactly?
As an another example let's examine what actually happens when we try to create a transformation plan between case classes that don't fit each other:
```scala mdoc
case class Car(brand: String, age: Int, noOfSeats: Long)

case class Plane(brand: String, noOfSeats: Long, age: Int, wingColor: String)
```

```scala mdoc:passthrough
import io.github.arainko.ducktape.docs.*

Docs.printPlan[Car, Plane]
```

We can see that in case something doesn't fully line up a `Plan` is still created but with a `Plan.Error` node somewhere inside, this is what the `E` type parameter of `Plan` is meant to represent (i.e. the possibility of being erroneous), as for why is it declared as covariant, it's due to one of Scala 3's new features.   

That feature being a proper support for GADTs in pattern matching, generally this means that for an enum declared as such:

```scala mdoc
enum Data[+E <: Throwable] {
  case NonFallible extends Data[Nothing]
  case SomeOtherStuff(value: String) extends Data[Nothing]
  case Error(error: Throwable) extends Data[Throwable]
}
```

...if we end up with a value of `Data[Nothing]` and pattern match on it the compiler will yell at us when we try to put `Data.Error` as one of the cases:
```scala mdoc:warn
val dataNonFallible: Data[Nothing] = Data.NonFallible

dataNonFallible match {
  case Data.NonFallible => "non fallible"
  case Data.SomeOtherStuff(value) => value
  case Data.Error(error) => "a warning will be issued on this line"
  // ^ Unreachable case 
}
```

But if we end up with a value of `Data[Throwable]` we will be forced to pattern match on all of the cases:
```scala mdoc:warn
val dataFallible: Data[Throwable] = Data.Error(Exception("woops"))

dataFallible match {
  case Data.NonFallible => "non fallible"
  case Data.SomeOtherStuff(value) => value
// ^ match may not be exhaustive.
// It would fail on pattern case: Data.Error(_, _)
}
```

Which brings us back to the usage of `+E <: Plan.Error` as way to keep track of possibly erroneous plans at compiletime (later on we will find out that to interpert a `Plan` into an actual transformation we need to feed the interpeter a `Plan[Nothing]` that is, a `Plan` without any `Plan.Error` nodes).


Now onto how a `Plan` is actually constructed, the very first two things you need are two `Structures` which are then plopped into a method called `Planner.create` which then does a big pattern match on those two `Structures` trying to extract information by matching on its subtypes and constructing plans accordingly. 
For example, given two `Structure.Products` a `Plan.BetweenProducts` will be constructed unless a user supplied instance of a `Transformer` is defined in the current implicit scope, in which case it takes precedence over any automatically constructed transformations.

<details>
  <summary>Click here to see more pattern matching</summary>

```scala
object Planner {
  import Structure.*

  def between(source: Structure, dest: Structure)(using Quotes, TransformationSite) = {
    given Depth = Depth.zero
    recurse(source, dest)
  }

  private def recurse(
    source: Structure,
    dest: Structure
  )(using quotes: Quotes, depth: Depth, transformationSite: TransformationSite): Plan[Plan.Error] = {
    import quotes.reflect.*
    given Depth = Depth.incremented(using depth)

    (source.force -> dest.force) match {
      case _ if Depth.current > 64 =>
        Plan.Error(source, dest, ErrorMessage.RecursionSuspected, None)

      case (source: Product, dest: Function) =>
        planProductFunctionTransformation(source, dest)

      case UserDefinedTransformation(transformer) =>
        Plan.UserDefined(source, dest, transformer)

      case (source, dest) if source.tpe.repr <:< dest.tpe.repr =>
        Plan.Upcast(source, dest)

      case (source @ Optional(_, _, srcParamStruct)) -> (dest @ Optional(_, _, destParamStruct)) =>
        Plan.BetweenOptions(
          source,
          dest,
          recurse(srcParamStruct, destParamStruct)
        )

      case source -> (dest @ Optional(_, _, paramStruct)) =>
        Plan.BetweenNonOptionOption(
          source,
          dest,
          recurse(source, paramStruct)
        )

      case (source @ Collection(_, _, srcParamStruct)) -> (dest @ Collection(_, _, destParamStruct)) =>
        Plan.BetweenCollections(
          source,
          dest,
          recurse(srcParamStruct, destParamStruct)
        )

      case (source: Product, dest: Product) =>
        planProductTransformation(source, dest)

      case (source: Coproduct, dest: Coproduct) =>
        planCoproductTransformation(source, dest)

      case (source: Structure.Singleton, dest: Structure.Singleton) if source.name == dest.name =>
        Plan.BetweenSingletons(source, dest)

      case (source: ValueClass, dest) if source.paramTpe.repr <:< dest.tpe.repr =>
        Plan.BetweenWrappedUnwrapped(source, dest, source.paramFieldName)

      case (source, dest: ValueClass) if source.tpe.repr <:< dest.paramTpe.repr =>
        Plan.BetweenUnwrappedWrapped(source, dest)

      case DerivedTransformation(transformer) =>
        Plan.Derived(source, dest, transformer)

      case (source, dest) =>
        Plan.Error(
          source,
          dest,
          ErrorMessage.CouldntBuildTransformation(source.tpe, dest.tpe),
          None
        )
    }
  }
}
```
See? I told you everything is just a big pattern match. If you require even more context head on over to [here](https://github.com/arainko/ducktape/blob/f12332ba907308c1a53ccafdc6a20b444665b11d/ducktape/src/main/scala/io/github/arainko/ducktape/internal/Planner.scala#L11) to see the code in an even nittier and grittier detail.
</details>

### The rest of the owl

Now onto the lower part of the graph.
<pre class="mermaid">
  flowchart TD
    InputExpr["Expr[Source]"]
    InitialPlan("Plan[Plan.Error]")
    InitialPlan -->|PlanRefiner.run| RefinedPlan("Either[NonEmptyList[Plan.Error], Plan[Nothing]]")
    RefinedPlan -->|Transformations.createTransformation| NonErroneousPlan("Plan[Nothing]")
    NonErroneousPlan -->|PlanInterpreter.run| ExpressionOfDest("Expr[Dest]")
    InputExpr -->|PlanInterpreter.run| ExpressionOfDest
</pre>

Going back to my previous tangent about `Plans` being possibly erroneous, having `Plan.Error` nodes inside our transformation `Plan` is a big no-no from the translate-to-actual-Scala-code point of view. What can you even do with an error node? Put a `???` in its place? Throw an exception? There's no good answer besides aborting the compilation but that would disallow us from reporting all of the errors at once.
The solution to that problem is refining a possibly erroneous `Plan[Plan.Error]` into a `Plan[Nothing]` (that enforces no `Plan.Error` nodes at compiletime!) while also collecting all of the `Plan.Error` nodes to report to the user.

The whole implementation comes down to recursively diving into the `Plan` tree and accumulating `Plan.Error` nodes we've encountered. At the very end if there aren't any error nodes we employ some dirty tricks and cast the input `Plan` to `Plan[Nothing]` (since the `E` param of `Plan` is effectively a phantom type) and pat ourselves on a back for doing such a good job.
<details>
  <summary>Click here if you want to see some pattern matching</summary>

```scala
object PlanRefiner {
  def run(plan: Plan[Plan.Error]): Either[NonEmptyList[Plan.Error], Plan[Nothing]] = {

    @tailrec
    def recurse(stack: List[Plan[Plan.Error]], errors: List[Plan.Error]): List[Plan.Error] =
      stack match {
        case head :: next =>
          head match {
            case plan: Plan.Upcast => 
              recurse(next, errors)
            case Plan.BetweenProducts(_, _, fieldPlans) =>
              recurse(fieldPlans.values.toList ::: next, errors)
            case Plan.BetweenCoproducts(_, _, casePlans) =>
              recurse(casePlans.toList ::: next, errors)
            case Plan.BetweenProductFunction(_, _, argPlans) =>
              recurse(argPlans.values.toList ::: next, errors)
            case Plan.BetweenOptions(_, _, plan)         => 
              recurse(plan :: next, errors)
            case Plan.BetweenNonOptionOption(_, _, plan) => 
              recurse(plan :: next, errors)
            case Plan.BetweenCollections(_, _, plan)     => 
              recurse(plan :: next, errors)
            case plan: Plan.BetweenSingletons            => 
              recurse(next, errors)
            case plan: Plan.UserDefined                  => 
              recurse(next, errors)
            case plan: Plan.Derived                      => 
              recurse(next, errors)
            case plan: Plan.Configured                   => 
              recurse(next, errors)
            case plan: Plan.BetweenWrappedUnwrapped      => 
              recurse(next, errors)
            case plan: Plan.BetweenUnwrappedWrapped      => 
              recurse(next, errors)
            case error: Plan.Error                       => 
              recurse(next, error :: errors)
          }
        case Nil => errors
      }
    val errors = recurse(plan :: Nil, Nil)
    // if no errors were accumulated that means there are no Plan.Error nodes which means we operate on a Plan[Nothing]
    NonEmptyList.fromList(errors).toLeft(plan.asInstanceOf[Plan[Nothing]])
  }
}

```
</details>

After the refinement is done we find ourselves with an `Either[NonEmptyList[Plan.Error], Plan[Nothing]]` in hand, we eliminate the left side by doing 🪄 magical 🪄 things with the errors (like lining up their positions, deduping, reporting and all that good stuff, very boring tho) - after that's done we're left with a `Plan[Nothing]` which means it's time to grind that plan into an AST with a `PlanInterpreter`.

Going back to an example from a [previous chapter](#the-makings-of-a-plan), the `Plan` showcased there will be expanded into this code:
```scala mdoc:passthrough
Docs.printCode(wirePerson.paymentMethods.head.to[domain.Payment])
```

If we squint hard enough we should be able to kind of see what each `Plan` maps to in terms of actual code, namely:
* `Plan.BetweenCoproducts` is expanded into an if expression with `.isInstanceOf` calls to determine the subtype and an expansion of the plan attached to a given case (along with a cast thrown in there for good measure),
* `Plan.BetweenProducts` is expanded into an invocation of the primary constructor with the fields recursively expanded under their respective names,
* `Plan.Upcast` just forwards the parameter it is given (since it implies that value fits as is),
* `Plan.BetweenSingletons` inserts the value of the destination singleton right then and there.

To quickly showcase the translation of other `Plan` nodes let's come up with another example

```scala mdoc:reset-object
final case class TwoWheeler(
  colorPalette: List[String],
  numberOfGears: Option[Long],
  seatColor: Option[String]
)

final case class Bike(
  numberOfGears: Long,
  colorPalette: Vector[Color], 
  seatColor: Option[Color]
)

final case class Color(value: String) extends AnyVal
```

Now, the `Plan` for a transformation between a `Bike` and a `TwoWheeler` would look as such:
```scala mdoc:passthrough
import io.github.arainko.ducktape.docs.*
Docs.printPlan[Bike, TwoWheeler]
```

There are a couple of new faces here, like `BetweenOptions`, `BetweenNonOptionOption`, `BetweenCollections` and `BetweenWrappedUnwrapped`, let's find out what they do from looking at the derived code:
```scala mdoc:passthrough
import io.github.arainko.ducktape.*
val bike = Bike(27, Vector.empty, None)
Docs.printCode(bike.to[TwoWheeler])
```

Having read all this we can conclude that:
* `Plan.BetweenCollection` expands into a `.map` call with a recursive expansion of a plan of the parameter and a `.to(DestCollectionFactory)` call at the end,
* `Plan.BetweenWrappedUnwrapped` unwraps a value class by getting the value of its single field,
* `Plan.BetweenNonOptionOption` wraps the expansion of the plan inside with a `Some.apply` (effectively just wrapping it),
* `Plan.BetweenOptions` maps over the option while also expanding the plan inside the lambda.

<details>
  <summary>The implementation itself is once again a giant pattern match</summary>

```scala
object PlanInterpreter {

  def run[A: Type](plan: Plan[Nothing], sourceValue: Expr[A])(using Quotes): Expr[Any] =
    recurse(plan, sourceValue)(using sourceValue)

  private def recurse[A: Type](plan: Plan[Nothing], value: Expr[Any])(using toplevelValue: Expr[A])(using Quotes): Expr[Any] = {
    import quotes.reflect.*

    plan match {
      case Plan.Upcast(_, _) => value

      case Plan.Configured(_, _, config) =>
        config match {
          case Configuration.Const(value, _) =>
            value
          case Configuration.CaseComputed(_, function) =>
            '{ $function.apply($value) }
          case Configuration.FieldComputed(_, function) =>
            '{ $function.apply($toplevelValue) }
          case Configuration.FieldReplacement(source, name, tpe) =>
            source.accessFieldByName(name).asExpr
        }

      case Plan.BetweenProducts(sourceTpe, destTpe, fieldPlans) =>
        val args = fieldPlans.map {
          case (fieldName, p: Plan.Configured) =>
            NamedArg(fieldName, recurse(p, value).asTerm)
          case (fieldName, plan) =>
            val fieldValue = value.accessFieldByName(fieldName).asExpr
            NamedArg(fieldName, recurse(plan, fieldValue).asTerm)
        }
        Constructor(destTpe.tpe.repr).appliedToArgs(args.toList).asExpr

      case Plan.BetweenCoproducts(sourceTpe, destTpe, casePlans) =>
        val branches = casePlans.map { plan =>
          (plan.source.tpe -> plan.dest.tpe) match {
            case '[src] -> '[dest] =>
              val sourceValue = '{ $value.asInstanceOf[src] }
              IfBranch(IsInstanceOf(value, plan.source.tpe), recurse(plan, sourceValue))
          }
        }.toList
        ifStatement(branches).asExpr

      case Plan.BetweenProductFunction(sourceTpe, destTpe, argPlans) =>
        val args = argPlans.map {
          case (fieldName, p: Plan.Configured) =>
            recurse(p, value).asTerm
          case (fieldName, plan) =>
            val fieldValue = value.accessFieldByName(fieldName).asExpr
            recurse(plan, fieldValue).asTerm
        }
        destTpe.function.appliedTo(args.toList)

      case Plan.BetweenOptions(sourceTpe, destTpe, plan) =>
        (sourceTpe.paramStruct.tpe -> destTpe.paramStruct.tpe) match {
          case '[src] -> '[dest] =>
            val optionValue = value.asExprOf[Option[src]]
            def transformation(value: Expr[src])(using Quotes): Expr[dest] = recurse(plan, value).asExprOf[dest]
            '{ $optionValue.map(src => ${ transformation('src) }) }
        }

      case Plan.BetweenNonOptionOption(sourceTpe, destTpe, plan) =>
        (sourceTpe.tpe -> destTpe.paramStruct.tpe) match {
          case '[src] -> '[dest] =>
            val sourceValue = value.asExprOf[src]
            def transformation(value: Expr[src])(using Quotes): Expr[dest] = recurse(plan, value).asExprOf[dest]
            '{ Some(${ transformation(sourceValue) }) }
        }

      case Plan.BetweenCollections(source, dest, plan) =>
        (dest.tpe, source.paramStruct.tpe, dest.paramStruct.tpe) match {
          case ('[destCollTpe], '[srcElem], '[destElem]) =>
            val sourceValue = value.asExprOf[Iterable[srcElem]]
            // TODO: Make it nicer, move this into Planner since we cannot be sure that a factory exists
            val factory = Expr.summon[Factory[destElem, destCollTpe]].get
            def transformation(value: Expr[srcElem])(using Quotes): Expr[destElem] = recurse(plan, value).asExprOf[destElem]
            '{ $sourceValue.map(src => ${ transformation('src) }).to($factory) }
        }

      case Plan.BetweenSingletons(sourceTpe, destTpe) => destTpe.value

      case Plan.BetweenWrappedUnwrapped(sourceTpe, destTpe, fieldName) =>
        value.accessFieldByName(fieldName).asExpr

      case Plan.BetweenUnwrappedWrapped(sourceTpe, destTpe) =>
        Constructor(destTpe.tpe.repr).appliedTo(value.asTerm).asExpr

      case Plan.UserDefined(source, dest, transformer) =>
        transformer match {
          case '{ $t: Transformer[src, dest] } =>
            val sourceValue = value.asExprOf[src]
            '{ $t.transform($sourceValue) }
        }

      case Plan.Derived(source, dest, transformer) =>
        transformer match {
          case '{ $t: Transformer.Derived[src, dest] } =>
            val sourceValue = value.asExprOf[src]
            '{ $t.transform($sourceValue) }
        }
    }
  }
}
```

If you're hungry for more here's a [link](https://github.com/arainko/ducktape/blob/blogpost-total-transformers/ducktape/src/main/scala/io/github/arainko/ducktape/internal/PlanInterpreter.scala) to the whole thing.
</details>

### That's it?

No, not really - I went past at least a single crucial step - configuration, that is, enabling the user to 'fix' broken `Plans` with a slew of config options like `Field.const`, `Case.computed` etc., so the graph shown in [Reifying all the stuff](#reifying-all-the-stuff) actually looks more like this:

<pre class="mermaid">
  flowchart TD
    Input1["Type.of[Source]"] -->|Structure.of| SourceStruct("Structure[Source]") 
    Input2["Type.of[Dest]"] -->|Structure.of| DestStruct("Structure[Dest]")
    InputExpr["Expr[Source]"]
    SourceStruct -->|Planner.create| InitialPlan("Plan[Plan.Error]")
    DestStruct -->|Planner.create| InitialPlan
    InputConfig["Field[Source, Dest] | Case[Source, Dest]*"] -->|Configuration.parse| Instructions("List[Configuration.Instruction]")
    InitialPlan -->|PlanConfigurer.run| ConfiguredPlan("Plan[Plan.Error]")
    Instructions -->|PlanConfigurer.run| ConfiguredPlan
    ConfiguredPlan -->|PlanRefiner.run| RefinedPlan("Either[NonEmptyList[Plan.Error], Plan[Nothing]]")
    RefinedPlan -->|Transformations.createTransformation| NonErroneousPlan("Plan[Nothing]")
    NonErroneousPlan -->|PlanInterpreter.run| ExpressionOfDest("Expr[Dest]")
    InputExpr -->|PlanInterpreter.run| ExpressionOfDest
</pre>


Then there's also `Fallible Transformers` which enable things like automatic validations and transformations from incomplete models (in the meantime you can see more on that [here](https://www.youtube.com/watch?v=jD2BMIaV_9c)).
I hope to try and touch upon those things in a future blogpost, but in the meantime give [ducktape 0.2.x](https://github.com/arainko/ducktape/tree/series/0.2.x) a try in your project!
