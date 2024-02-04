package io.github.arainko.ducktape.fallible.failfast

import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.fallible.FallibleTransformer

class FastFailCoproductTransformationsSuite extends DucktapeSuite {
  type ErrorsOrResult = [X] =>> Either[String, X]

  given Transformer.Mode.FailFast[ErrorsOrResult] =
    Transformer.Mode.FailFast.either[String]

  given deriveMandatoryOptionTransformer[A, B](using
    transformer: FallibleTransformer[ErrorsOrResult, A, B]
  ): FallibleTransformer[ErrorsOrResult, Option[A], B] = {
    case Some(a) => transformer.transform(a)
    case None    => Left("Missing required field")
  }

  test("Derive sum of products") {
    sealed trait From
    object From {
      case class Product(field: Option[Int]) extends From
      case class Product2(field: Option[Int]) extends From
    }

    sealed trait To
    object To {
      case class Product(field: Int) extends To
      case class Product2(field: Int) extends To
    }

    val transformer = FallibleTransformer.betweenCoproductsFailFast[ErrorsOrResult, From, To]

    assert(transformer.transform(From.Product(Some(1))) == Right(To.Product(1)))
    assert(transformer.transform(From.Product(None)) == Left("Missing required field"))
    assert(transformer.transform(From.Product2(Some(2))) == Right(To.Product2(2)))
  }

  test("Derive sum of singletons") {
    sealed trait From
    object From {
      case object Singleton extends From
      case object Singleton2 extends From
    }

    sealed trait To
    object To {
      case object Singleton extends To
      case object Singleton2 extends To
    }

    val transformer = FallibleTransformer.betweenCoproductsFailFast[ErrorsOrResult, From, To]

    assert(transformer.transform(From.Singleton) == Right(To.Singleton))
    assert(transformer.transform(From.Singleton2) == Right(To.Singleton2))
  }

  test("Derive sum of singleton and product") {
    sealed trait From
    object From {
      case class Product(field: Option[Int]) extends From

      case object Singleton extends From
    }

    sealed trait To
    object To {
      case class Product(field: Int) extends To
      case object Singleton extends To
    }

    val transformer = FallibleTransformer.betweenCoproductsFailFast[ErrorsOrResult, From, To]

    assert(transformer.transform(From.Singleton) == Right(To.Singleton))
    assert(transformer.transform(From.Product(Some(5))) == Right(To.Product(5)))
    assert(transformer.transform(From.Product(None)) == Left("Missing required field"))
  }

  test("Fail with the first problems") {
    sealed trait From
    object From {
      case class Product(field1: Option[Int], field2: Option[String]) extends From
    }

    sealed trait To
    object To {
      case class Product(field1: Int, field2: String) extends To
    }

    val transformer = FallibleTransformer.betweenCoproductsFailFast[ErrorsOrResult, From, To]

    assert(transformer.transform(From.Product(None, None)) == Left("Missing required field"))
    assert(transformer.transform(From.Product(Some(1), None)) == Left("Missing required field"))
    assert(transformer.transform(From.Product(None, Some("s"))) == Left("Missing required field"))
    assert(transformer.transform(From.Product(Some(1), Some("s"))) == Right(To.Product(1, "s")))
  }

  test("Derivation fails if the case names don't align") {
    sealed trait From
    object From {
      case class Product() extends From
    }

    sealed trait To
    object To {
      case class ProductWrongName() extends To
    }

    assertFailsToCompileWith {
      "FallibleTransformer.betweenCoproductsFailFast[ErrorsOrResult, From, To]"
    }("No child named 'Product' found in To")
  }

  test("Derivation fails if a case can't be transformed") {
    sealed trait From
    object From {
      case class Product(s: String) extends From
    }

    sealed trait To
    object To {
      case class Product(y: String) extends To
    }

    assertFailsToCompileWith {
      "FallibleTransformer.betweenCoproductsFailFast[ErrorsOrResult, From, To]"
    }(
      "Neither an instance of Transformer.Fallible[ErrorsOrResult, From.Product, To.Product] was found\nnor are 'Product' 'Product' singletons with the same name"
    )
  }

  test("Fallible config options can fill in missing cases") {
    sealed trait From
    object From {
      case class Product(field1: Option[Int], field2: Option[String]) extends From
      case object Extra extends From
    }

    sealed trait To
    object To {
      case class Product(field1: Int, field2: String) extends To
    }

    val from: From = From.Extra

    val actualConst =
      from
        .into[To]
        .fallible
        .transform(
          Case.fallibleConst[From.Extra.type](Left("ahh well"))
        )

    def mappingComputed(from: From.Extra.type): Either[String, To] = Left("ahh well")

    val actualComputed =
      from
        .into[To]
        .fallible
        .transform(
          Case.fallibleComputed[From.Extra.type](mappingComputed)
        )

    val expected = Left("ahh well")

    assertEquals(actualConst, expected)
    assertEquals(actualComputed, expected)
  }

  test("Total config options can fill in missing cases") {
    sealed trait From
    object From {
      case class Product(field1: Option[Int], field2: Option[String]) extends From
      case object Extra extends From
    }

    sealed trait To
    object To {
      case class Product(field1: Int, field2: String) extends To
    }

    val from: From = From.Extra

    val actualConst =
      from
        .into[To]
        .fallible
        .transform(
          Case.const[From.Extra.type](To.Product(1, "asd"))
        )

    def mappingComputed(from: From.Extra.type): To = To.Product(1, "asd")

    val actualComputed =
      from
        .into[To]
        .fallible
        .transform(
          Case.computed[From.Extra.type](mappingComputed)
        )

    val expected = Right(To.Product(1, "asd"))

    assertEquals(actualConst, expected)
    assertEquals(actualComputed, expected)
  }
}
