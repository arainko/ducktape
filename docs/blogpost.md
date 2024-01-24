# Is ducktape still all duct tape under the hood; or, why are macros so cool that I'm basically rewriting it for the third time?

### Prelude

Before I go off talking about the insides of the library, let's first touch base on what `ducktape` actually is, [its Github page](https://github.com/arainko/ducktape/tree/series/0.2.x) describes it as this:

```
Automatic and customizable compile time transformations between similar case classes and sealed traits/enums, essentially a thing that glues your code.
```

The last part of that sentence also shines some light on why the name is so dumb, it was originally supposed to be called `ducttape` and signify its potential of generating *glue* code for you. 

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
</details>

I'd rather stare at a CI pipeline that fails after 18 minutes because I forgot to format my code.

### Previous 'art'

As hinted in the overlong title, which is itself inspired by the title of this album (go listen to it, it's got a cool lizard on the cover):
<iframe style="border: 0; width: 100%; height: 120px;" src="https://bandcamp.com/EmbeddedPlayer/album=3699259806/size=large/bgcol=ffffff/linkcol=0687f5/tracklist=false/artwork=small/transparent=true/" seamless><a href="https://kinggizzard.bandcamp.com/album/petrodragonic-apocalypse-or-dawn-of-eternal-night-an-annihilation-of-planet-earth-and-the-beginning-of-merciless-damnation">PetroDragonic Apocalypse;  or,  Dawn of Eternal Night:  An Annihilation of Planet Earth and the Beginning of Merciless Damnation by King Gizzard &amp; The Lizard Wizard</a></iframe>

the library has been through 2 phases already.

The first of which is documented in [this blogpost](https://scalac.io/blog/inline-your-boilerplate-harnessing-scala3-metaprogramming-without-macros/) and can be summarized as match type abuse (the `0.0.x` line of releases).

The second one being the `0.1.x` line of releases which pretty much scrapped all traces of its predecessor and replaced it with those pesky macros and developed an overreliance on automatic typeclass derivation which then had to be unpacked in a process I can only call ['beta-reduction at home'](https://github.com/arainko/ducktape/blob/5d266a7c9076037e6512c0a740b2065e4f077828/ducktape/src/main/scala/io/github/arainko/ducktape/internal/macros/LiftTransformation.scala#L113)  to not generate unnecessary `Transformer` (the typeclass being automatically derived) instances at runtime. All in all, a pretty fun piece of code.

The third and newest iteration is the `0.2.x` line or releases (sitting at a `Milestone 2` release at the time of writing) - this time I took a more thought-through approach to structuring the library than constantly telling the compiler to derive that good good.

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
    InputConfig["Field[Source, Dest] | Case[Source, Dest]*"] -->|Configuration.parse| Instructions("List[Configuration.Instruction]")
    InitialPlan -->|PlanConfigurer.run| ConfiguredPlan("Plan[Plan.Error]")
    Instructions -->|PlanConfigurer.run| ConfiguredPlan
    ConfiguredPlan -->|PlanRefiner.run| RefinedPlan("Either[NonEmptyList[Plan.Error], Plan[Nothing]]")
    RefinedPlan -->|Transformations.createTransformation| NonErroneousPlan("Plan[Nothing]")
    NonErroneousPlan -->|PlanInterpreter.run| ExpressionOfDest("Expr[Dest]")
    InputExpr -->|PlanInterpreter.run| ExpressionOfDest
</pre>

<script type="module">
  import mermaid from 'https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.esm.min.mjs';
</script>

So, what is all this stuff you may ask and am I even showing you this... Let's try to peel it back layer by layer starting from the topmost-left piece of the graph, i.e:

<pre class="mermaid">
  flowchart TD
    Input1["Type.of[Source]"] -->|Structure.of| SourceStruct("Structure[Source]") 
    Input2["Type.of[Dest]"] -->|Structure.of| DestStruct("Structure[Dest]")
    SourceStruct -->|Planner.create| InitialPlan("Plan[Plan.Error]")
    DestStruct -->|Planner.create| InitialPlan
</pre>

### The structure of a `Structure`

A `Structure` is meant to capture stuff like fields of a case class (their names and `Structures` that correspond to them) , children of an enum/sealed trait and some more specialized stuff like `Optional` types, `Collections`, `Value Classes`, `Singletons` etc. ([the implementation itself looks like this](https://github.com/arainko/ducktape/blob/1cdf94d497f071a4f42269a80a2bf999eb27815b/ducktape/src/main/scala/io/github/arainko/ducktape/internal/Structure.scala)), so going back to our [Motivating Example](#motivating-example), a `Structure` of `wire.Person` looks like this:

```scala mdoc:passthrough
import io.github.arainko.ducktape.docs.*

Docs.printStructure[wire.Person]
```
So uhhh, that doesn't tell us anything since most of that stuff is `Lazy`, but it's lazy for a reason not because it doesn't want to do anything, that reason being recursive types which are suspiciously prevalent in our day to day life, looking no further than Scala's `List`. See, if it wasn't for those `Lazy` nodes we'd be sending the compiler on a trip it wouldn't be coming back from, but if we encode the notion of laziness we can expand those calls later on when we'll be taking special care to not overflow the stack.
