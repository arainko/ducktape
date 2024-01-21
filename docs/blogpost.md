# Is ducktape still all duct tape under the hood; or, why are macros so cool that I'm basically rewriting it for the third time?

### Prelude

Before I go off talking about the insides of the library, let's first touch base on what `ducktape` actually is, [its Github page](https://github.com/arainko/ducktape/tree/series/0.2.x) describes it as this:

```
Automatic and customizable compile time transformations between similar case classes and sealed traits/enums, essentially a thing that glues your code.
```

The last part of that sentence also shines some light on why the name is so dumb, it was originally supposed to be called `ducttape` and signify its potential of generating *glue* code for you. 

However, the idea of joining two words that end and start with the same letter wouldn't let me sleep, so I went with what I considered an absolute kino of a name at the time - `ducktape`, but I digress.

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

The second one being the `0.1.x` line of releases which pretty much scrapped all traces of its predecessor and replaced it with those pesky macros and developed an overreliance on automatic typeclass derivation which then had to be unpacked in a process I can only call 'beta-reduction at home' (TODO: Add GH permalink to `LiftTransformation.scala`) to not generate unnecessary `Transformer` (the typeclass being automatically derived) instances at runtime. All in all, a pretty fun piece of code.

The third and newest iteration is the `0.2.x` line or releases (sitting at a `Milestone 2` release at the time of writing) - this time I took a more thought-through approach to structuring the library than constantly telling the compiler to derive that good good.

The main motivation was being able to support stuff like nested configuration of fields and cases (which IMO were the worst offenders to usability of the library itself since even if your transformation was aaaaalmost there but a single field was missing in a nested case class you were done for and had to create a new `Transformer` instance and put it in implicit scope) and being able to show the user all of the failures that occurred all at once in addition to being more actionable than just `Yeah, the field 'chips' is missing in Diner`.

### Reifying all the stuff

Most of the issues of `0.1.x` came from relying on automatic derivation of `Transformers` to do basically everything, which resulted in the library not really being in control of anything below the very first level of the transformation since the compiler is pretty much a magic blackbox that does stuff for you, so to be able to do all of the things listed above I had to find a way of introspecting and somehow transforming the transformations.

Reifying (being a synonym of shoving stuff into data structures instead of immediately taking action on it to then 'interpret' them at the very end) 
