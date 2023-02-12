# ducktape

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.github.arainko/ducktape_3/badge.svg?style=flat-square)](https://maven-badges.herokuapp.com/maven-central/io.github.arainko/ducktape_3)

*ducktape* is a library for boilerplate-less and configurable transformations between case classes and enums/sealed traits for Scala 3. Directly inspired by [chimney](https://github.com/scalalandio/chimney).

If this project interests you, please drop a 🌟 - these things are worthless but give me a dopamine rush nonetheless.

### Installation
```scala
libraryDependencies += "io.github.arainko" %% "ducktape" % "0.1.3"
```
NOTE: the [version scheme](https://www.scala-lang.org/blog/2021/02/16/preventing-version-conflicts-with-versionscheme.html) is set to `early-semver`

### Examples

#### 1. *Case class to case class*

```scala
import io.github.arainko.ducktape.*

final case class Person(firstName: String, lastName: String, age: Int)
final case class PersonButMoreFields(firstName: String, lastName: String, age: Int, socialSecurityNo: String)

val personWithMoreFields = PersonButMoreFields("John", "Doe", 30, "SOCIAL-NUM-12345")
// personWithMoreFields: PersonButMoreFields = PersonButMoreFields(
//   firstName = "John",
//   lastName = "Doe",
//   age = 30,
//   socialSecurityNo = "SOCIAL-NUM-12345"
// )

val transformed = personWithMoreFields.to[Person]
// transformed: Person = Person(firstName = "John", lastName = "Doe", age = 30)
```

Automatic case class to case class transformations are supported given that
the source type has all the fields of the destination type and the types corresponding to these fields have an instance of `Transformer` in scope.

If these requirements are not met, a compiletime error is issued:
```scala
val person = Person("Jerry", "Smith", 20)

person.to[PersonButMoreFields]

// error:
// No field named 'socialSecurityNo' found in Person
//     .into[Person2]
//                   ^
```

#### 2. *Enum to enum*

```scala
import io.github.arainko.ducktape.*

enum Size:
  case Small, Medium, Large

enum ExtraSize:
  case ExtraSmall, Small, Medium, Large, ExtraLarge

val transformed = Size.Small.to[ExtraSize]
// transformed: ExtraSize = Small
```

We can't go to a coproduct that doesn't contain all of our cases (name wise):

```scala
val size = ExtraSize.Small.to[Size]
// error:
// No child named 'ExtraSmall' in Size
```

Automatic enum to enum transformations are supported given that the destination enum contains a subset of cases
we want to transform into, otherwise a compiletime errors is issued.

#### 3. *Case class to case class with config*

As we established earlier, going from `Person` to `PersonButMoreFields` cannot happen automatically as the former
doesn't have the `socialSecurityNo` field, but it has all the other fields - so it's almost there, we just have to nudge it a lil' bit.

We can do so with field configurations in 3 ways:
  1. Set a constant to a specific field with `Field.const`
  2. Compute the value for a specific field by applying a function with `Field.computed`
  3. Use a different field in its place - 'rename' it with `Field.renamed`
  4. Grab all matching fields from another case class with `Field.allMatching`

```scala
import io.github.arainko.ducktape.*

final case class Person(firstName: String, lastName: String, age: Int)
final case class PersonButMoreFields(firstName: String, lastName: String, age: Int, socialSecurityNo: String)

val person = Person("Jerry", "Smith", 20)
// person: Person = Person(firstName = "Jerry", lastName = "Smith", age = 20)

// 1. Set a constant to a specific field
val withConstant = 
  person
    .into[PersonButMoreFields]
    .transform(Field.const(_.socialSecurityNo, "CONSTANT-SSN"))
// withConstant: PersonButMoreFields = PersonButMoreFields(
//   firstName = "Jerry",
//   lastName = "Smith",
//   age = 20,
//   socialSecurityNo = "CONSTANT-SSN"
// )

// 2. Compute the value for a specific field by applying a function
val withComputed = 
  person
    .into[PersonButMoreFields]
    .transform(Field.computed(_.socialSecurityNo, p => s"${p.firstName}-COMPUTED-SSN"))
// withComputed: PersonButMoreFields = PersonButMoreFields(
//   firstName = "Jerry",
//   lastName = "Smith",
//   age = 20,
//   socialSecurityNo = "Jerry-COMPUTED-SSN"
// )

// 3. Use a different field in its place - 'rename' it
val withRename = 
  person
    .into[PersonButMoreFields]
    .transform(Field.renamed(_.socialSecurityNo, _.firstName))
// withRename: PersonButMoreFields = PersonButMoreFields(
//   firstName = "Jerry",
//   lastName = "Smith",
//   age = 20,
//   socialSecurityNo = "Jerry"
// )

final case class FieldSource(lastName: String, socialSecurityNo: String)

// 4. Grab and use all matching fields from a different case class (a compiletime error will be issued if none of the fields match)
val withAllMatchingFields = 
  person
    .into[PersonButMoreFields]
    .transform(Field.allMatching(FieldSource("SourcedLastName", "SOURCED-SSN")))
// withAllMatchingFields: PersonButMoreFields = PersonButMoreFields(
//   firstName = "Jerry",
//   lastName = "SourcedLastName",
//   age = 20,
//   socialSecurityNo = "SOURCED-SSN"
// )
```

In case we repeatedly apply configurations to the same field, the latest one is chosen:

```scala
val withRepeatedConfig =
  person
    .into[PersonButMoreFields]
    .transform(
      Field.renamed(_.socialSecurityNo, _.firstName),
      Field.computed(_.socialSecurityNo, p => s"${p.firstName}-COMPUTED-SSN"),
      Field.allMatching(FieldSource("SourcedLastName", "SOURCED-SSN")),
      Field.const(_.socialSecurityNo, "CONSTANT-SSN")
    )
// withRepeatedConfig: PersonButMoreFields = PersonButMoreFields(
//   firstName = "Jerry",
//   lastName = "SourcedLastName",
//   age = 20,
//   socialSecurityNo = "CONSTANT-SSN"
// )
```

Of course we can use this to override the automatic derivation for each field:

```scala
val withEverythingOverriden = 
  person
    .into[PersonButMoreFields]
    .transform(
      Field.const(_.socialSecurityNo, "CONSTANT-SSN"),
      Field.const(_.age, 100),
      Field.const(_.firstName, "OVERRIDEN-FIRST-NAME"),
      Field.const(_.lastName, "OVERRIDEN-LAST-NAME"),
    )
// withEverythingOverriden: PersonButMoreFields = PersonButMoreFields(
//   firstName = "OVERRIDEN-FIRST-NAME",
//   lastName = "OVERRIDEN-LAST-NAME",
//   age = 100,
//   socialSecurityNo = "CONSTANT-SSN"
// )
```

#### 4. Enum to enum with config

Enum transformations, just like case class transformations, can be configured by:
* supplying a constant value with `Case.const`,
* supplying a function that will be applied to the chosen subtype with `Case.computed`.

```scala
import io.github.arainko.ducktape.*

enum Size:
  case Small, Medium, Large

enum ExtraSize:
  case ExtraSmall, Small, Medium, Large, ExtraLarge

// Specify a constant for the cases that are not covered automatically
val withConstants = 
  ExtraSize.ExtraSmall
    .into[Size]
    .transform(
      Case.const[ExtraSize.ExtraSmall.type](Size.Small),
      Case.const[ExtraSize.ExtraLarge.type](Size.Large)
    )
// withConstants: Size = Small

// Specify a function to transform a given case with that function
val withComputed =
  ExtraSize.ExtraSmall
    .into[Size]
    .transform(
      Case.computed[ExtraSize.ExtraSmall.type](_ => Size.Small),
      Case.computed[ExtraSize.ExtraLarge.type](_ => Size.Large)
    )
// withComputed: Size = Small
```

#### 5. Method to case class

We can also let `ducktape` expand method incovations for us:

```scala
import io.github.arainko.ducktape.*

final case class Person1(firstName: String, lastName: String, age: Int)
final case class Person2(firstName: String, lastName: String, age: Int)

def methodToExpand(lastName: String, age: Int, firstName: String): Person2 =
  Person2(firstName, lastName, age)

val person1: Person1 = Person1("John", "Doe", 23)
// person1: Person1 = Person1(firstName = "John", lastName = "Doe", age = 23)
val person2: Person2 = person1.via(methodToExpand)
// person2: Person2 = Person2(firstName = "John", lastName = "Doe", age = 23)
```

In this case, `ducktape` will match the fields from `Person` to parameter names of `methodToExpand` failing at compiletime if
a parameter cannot be matched (be it there's no name correspondence or a `Transformer` between types of two fields with the same name isn't available):

```scala
def methodToExpandButOneMoreArg(lastName: String, age: Int, firstName: String, additionalArg: String): Person2 =
  Person2(firstName + additionalArg, lastName, age)

person1.via(methodToExpandButOneMoreArg)
// error:
// No field named 'additionalArg' in Person
```

#### 6. Method to case class with config

Just like transforming between case classes and coproducts we can nudge the derivation in some places to complete the puzzle, let's
tackle the last example once again:

```scala
def methodToExpandButOneMoreArg(lastName: String, age: Int, firstName: String, additionalArg: String): Person2 =
  Person2(firstName + additionalArg, lastName, age)

val withConstant = 
  person1
    .intoVia(methodToExpandButOneMoreArg)
    .transform(Arg.const(_.additionalArg, "-CONST ARG"))
// withConstant: Person2 = Person2(
//   firstName = "John-CONST ARG",
//   lastName = "Doe",
//   age = 23
// )

val withComputed = 
  person1
    .intoVia(methodToExpandButOneMoreArg)
    .transform(Arg.computed(_.additionalArg, _.lastName + "-COMPUTED"))
// withComputed: Person2 = Person2(
//   firstName = "JohnDoe-COMPUTED",
//   lastName = "Doe",
//   age = 23
// )

val withRenamed = 
  person1
    .intoVia(methodToExpandButOneMoreArg)
    .transform(Arg.renamed(_.additionalArg, _.lastName))
// withRenamed: Person2 = Person2(
//   firstName = "JohnDoe",
//   lastName = "Doe",
//   age = 23
// )
```

#### 7. Automatic wrapping and unwrapping of `AnyVal`

Despite being a really flawed abstraction `AnyVal` is pretty prevalent in Scala 2 code that you may want to interop with
and `ducktape` is here to assist you. `Transformer` definitions for wrapping and uwrapping `AnyVals` are
automatically available:

```scala
import io.github.arainko.ducktape.*

final case class WrappedString(value: String) extends AnyVal

val wrapped = WrappedString("I am a String")
// wrapped: WrappedString = WrappedString(value = "I am a String")

val unwrapped = wrapped.to[String]
// unwrapped: String = "I am a String"

val wrappedAgain = unwrapped.to[WrappedString]
// wrappedAgain: WrappedString = WrappedString(value = "I am a String")
```

#### 8. Defining custom `Transformers`

If for some reason you need a custom `Transformer` in scope but still want to partially rely
on the automatic derivation and have all the configuration DSL goodies you can use these:

* `Transformer.define[Source, Dest].build(<Field/Case configuration>)`
* `Transformer.defineVia[Source](someMethod).build(<Arg configuration>)`
  
Examples:

```scala
import io.github.arainko.ducktape.*

final case class TestClass(str: String, int: Int)
final case class TestClassWithAdditionalList(int: Int, str: String, additionalArg: List[String])

def method(str: String, int: Int, additionalArg: List[String]) = TestClassWithAdditionalList(int, str, additionalArg)

val testClass = TestClass("str", 1)
// testClass: TestClass = TestClass(str = "str", int = 1)

val definedViaTransformer =
  Transformer
    .defineVia[TestClass](method)
    .build(Arg.const(_.additionalArg, List("const")))
// definedViaTransformer: Transformer[TestClass, TestClassWithAdditionalList] = repl.MdocSession$MdocApp6$$Lambda$38661/0x0000000106842c40@680998bb

val definedTransformer =
  Transformer
    .define[TestClass, TestClassWithAdditionalList]   
    .build(Field.const(_.additionalArg, List("const")))
// definedTransformer: Transformer[TestClass, TestClassWithAdditionalList] = repl.MdocSession$MdocApp6$$Lambda$38662/0x0000000106840040@44a6c67d

val transformedVia = definedViaTransformer.transform(testClass)
// transformedVia: TestClassWithAdditionalList = TestClassWithAdditionalList(
//   int = 1,
//   str = "str",
//   additionalArg = List("const")
// )

val transformed = definedTransformer.transform(testClass)
// transformed: TestClassWithAdditionalList = TestClassWithAdditionalList(
//   int = 1,
//   str = "str",
//   additionalArg = List("const")
// )
```

#### Usecase: recursive `Transformers`

Recursive instances are lazy by nature so automatic derivation will be of no use here, we need to get our hands a little bit dirty:

```scala
import io.github.arainko.ducktape.*

final case class Rec[A](value: A, rec: Option[Rec[A]])

given recursive[A, B](using Transformer[A, B]): Transformer[Rec[A], Rec[B]] = 
  Transformer.define[Rec[A], Rec[B]].build()

Rec("1", Some(Rec("2", Some(Rec("3", None))))).to[Rec[Option[String]]]
// res8: Rec[Option[String]] = Rec(
//   value = Some(value = "1"),
//   rec = Some(
//     value = Rec(
//       value = Some(value = "2"),
//       rec = Some(value = Rec(value = Some(value = "3"), rec = None))
//     )
//   )
// )
```

### A look at the generated code

To inspect the code that is generated you can use `Transformer.Debug.showCode`, this method will print 
the generated code at compile time for you to analyze and see if there's something funny going on after the macro expands.

For the sake of documentation let's also give some examples of what should be the expected output for some basic usages of `ducktape`.

#### Generated code - product transformations
Given a structure of case classes like the ones below let's examine the output that `ducktape` splices into your code:

```scala
import io.github.arainko.ducktape.*

final case class Wrapped[A](value: A) extends AnyVal

case class Person(int: Int, str: Option[String], inside: Inside, collectionOfNumbers: Vector[Float])
case class Person2(int: Wrapped[Int], str: Option[Wrapped[String]], inside: Inside2, collectionOfNumbers: List[Wrapped[Float]])

case class Inside(str: String, int: Int, inside: EvenMoreInside)
case class Inside2(int: Int, str: String, inside: Option[EvenMoreInside2])

case class EvenMoreInside(str: String, int: Int)
case class EvenMoreInside2(str: String, int: Int)

val person = Person(23, Some("str"), Inside("insideStr", 24, EvenMoreInside("evenMoreInsideStr", 25)), Vector.empty)
```
#### Generated code - expansion of `.to`
Calling the `.to` method
```scala
person.to[Person2]
```
expands to:
``` scala 
  to[Person](person)[Person2](
    inline$make$i1[Person, Person2](ForProduct)(
      (
        (source: Person) =>
          new Person2(
            int = new Wrapped[Int](source.int),
            str = source.str.map[Wrapped[String]]((src: String) => new Wrapped[String](src)),
            inside = new Inside2(
              int = source.inside.int,
              str = source.inside.str,
              inside =
                Some.apply[EvenMoreInside2](new EvenMoreInside2(str = source.inside.inside.str, int = source.inside.inside.int))
            ),
            collectionOfNumbers = source.collectionOfNumbers
              .map[Wrapped[Float]]((`src₂`: Float) => new Wrapped[Float](`src₂`))
              .to[List[Wrapped[Float]] & Iterable[Wrapped[Float]]](iterableFactory[Wrapped[Float]])
          )
      ): Transformer[Person, Person2]
    ): ForProduct[Person, Person2]
  )
```

#### Generated code - expansion of `.into`
Calling the `.into` method
```scala
person
  .into[Person2]
  .transform(
    Field.const(_.str, Some(Wrapped("ConstString!"))),
    Field.computed(_.int, person => Wrapped(person.int + 100)),
  )
```
expands to:
``` scala 
  {
    val AppliedBuilder_this: AppliedBuilder[Person, Person2] = into[Person](person)[Person2]

    {
      val source$proxy13: Person = AppliedBuilder_this.inline$appliedTo

      {
        val inside$2: Inside2 = new Inside2(
          int = source$proxy13.inside.int,
          str = source$proxy13.inside.str,
          inside = Some.apply[EvenMoreInside2](
            new EvenMoreInside2(str = source$proxy13.inside.inside.str, int = source$proxy13.inside.inside.int)
          )
        )
        val collectionOfNumbers$2: List[Wrapped[Float]] = source$proxy13.collectionOfNumbers
          .map[Wrapped[Float]]((src: Float) => new Wrapped[Float](src))
          .to[List[Wrapped[Float]] & Iterable[Wrapped[Float]]](iterableFactory[Wrapped[Float]])
        val str$2: Some[Wrapped[String]] = Some.apply[Wrapped[String]](Wrapped.apply[String]("ConstString!"))
        val int$2: Wrapped[Int] = Wrapped.apply[Int](source$proxy13.int.+(100))
        new Person2(int = int$2, str = str$2, inside = inside$2, collectionOfNumbers = collectionOfNumbers$2)
      }: Person2
    }: Person2
  }
```

#### Generated code - expansion of `.via`
Calling the `.via` method
```scala
person.via(Person2.apply)
```

expands to:
``` scala 
  {
    val Func$proxy4: FunctionMirror[Function4[Wrapped[Int], Option[Wrapped[String]], Inside2, List[Wrapped[Float]], Person2]] {
      type Return >: Person2 <: Person2
    } = FunctionMirror.asInstanceOf[
      FunctionMirror[Function4[Wrapped[Int], Option[Wrapped[String]], Inside2, List[Wrapped[Float]], Person2]] {
        type Return >: Person2 <: Person2
      }
    ]

    ({
      val int$proxy2: Wrapped[Int] = new Wrapped[Int](person.int)
      val str$proxy2: Option[Wrapped[String]] = person.str.map[Wrapped[String]]((src: String) => new Wrapped[String](src))
      val inside$proxy2: Inside2 = new Inside2(
        int = person.inside.int,
        str = person.inside.str,
        inside = Some.apply[EvenMoreInside2](new EvenMoreInside2(str = person.inside.inside.str, int = person.inside.inside.int))
      )
      val collectionOfNumbers$proxy2: List[Wrapped[Float]] = person.collectionOfNumbers
        .map[Wrapped[Float]]((`src₂`: Float) => new Wrapped[Float](`src₂`))
        .to[List[Wrapped[Float]] & Iterable[Wrapped[Float]]](iterableFactory[Wrapped[Float]])
      Person2.apply(int$proxy2, str$proxy2, inside$proxy2, collectionOfNumbers$proxy2)
    }: Return): Return
  }
```

#### Generated code - expansion of `.intoVia`
Calling the `.intoVia` method with subsequent transformation customizations
```scala
person
  .intoVia(Person2.apply)
  .transform(
    Arg.const(_.str, Some(Wrapped("ConstStr!"))),
    Arg.computed(_.int, person => Wrapped(person.int + 100))
  )
```

expands to:
``` scala 
  {
  val x$4$proxy5: FunctionMirror[Function4[Wrapped[Int], Option[Wrapped[String]], Inside2, List[Wrapped[Float]], Person2]] {
    type Return >: Person2 <: Person2
  } = FunctionMirror.asInstanceOf[FunctionMirror[Function4[Wrapped[Int], Option[Wrapped[String]], Inside2, List[Wrapped[Float]], Person2]] {
    type Return >: Person2 <: Person2
  }]
  val builder: AppliedViaBuilder[Person, Return, Function4[Wrapped[Int], Option[Wrapped[String]], Inside2, List[Wrapped[Float]], Person2], Nothing] = inline$instance[Person, x$4$proxy5.Return, Function4[Wrapped[Int], Option[Wrapped[String]], Inside2, List[Wrapped[Float]], Person2], Nothing](person, ((int: Wrapped[Int], str: Option[Wrapped[String]], inside: Inside2, collectionOfNumbers: List[Wrapped[Float]]) => Person2.apply(int, str, inside, collectionOfNumbers)))
  val AppliedViaBuilder_this: AppliedViaBuilder[Person, Person2, Function4[Wrapped[Int], Option[Wrapped[String]], Inside2, List[Wrapped[Float]], Person2], FunctionArguments {
    val int: Wrapped[Int]
    val str: Option[Wrapped[String]]
    val inside: Inside2
    val collectionOfNumbers: List[Wrapped[Float]]
  }] = builder.asInstanceOf[[ArgSelector >: Nothing <: FunctionArguments] => AppliedViaBuilder[Person, Return, Function4[Wrapped[Int], Option[Wrapped[String]], Inside2, List[Wrapped[Float]], Person2], ArgSelector][FunctionArguments {
    val int: Wrapped[Int]
    val str: Option[Wrapped[String]]
    val inside: Inside2
    val collectionOfNumbers: List[Wrapped[Float]]
  }]]

  ({
    val source$proxy15: Person = AppliedViaBuilder_this.inline$source

    (AppliedViaBuilder_this.inline$function.apply(Wrapped.apply[Int](source$proxy15.int.+(100)), Some.apply[Wrapped[String]](Wrapped.apply[String]("ConstStr!")), new Inside2(int = source$proxy15.inside.int, str = source$proxy15.inside.str, inside = Some.apply[EvenMoreInside2](new EvenMoreInside2(str = source$proxy15.inside.inside.str, int = source$proxy15.inside.inside.int))), source$proxy15.collectionOfNumbers.map[Wrapped[Float]](((src: Float) => new Wrapped[Float](src))).to[List[Wrapped[Float]] & Iterable[Wrapped[Float]]](iterableFactory[Wrapped[Float]])): Person2)
  }: Person2)
}
```
