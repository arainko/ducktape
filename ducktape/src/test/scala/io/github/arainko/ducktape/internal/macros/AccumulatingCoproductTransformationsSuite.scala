package io.github.arainko.ducktape.internal.macros

import io.github.arainko.ducktape.fallible.FallibleTransformer
import io.github.arainko.ducktape.{ DucktapeSuite, Transformer }

import scala.deriving.Mirror

class AccumulatingCoproductTransformationsSuite extends DucktapeSuite {
  type ErrorsOrResult = [X] =>> Either[List[String], X]

  given Transformer.Mode.Accumulating[ErrorsOrResult] =
    Transformer.Mode.Accumulating.either[String, List]

  given deriveMandatoryOptionTransformer[A, B](using
    transformer: FallibleTransformer[ErrorsOrResult, A, B]
  ): FallibleTransformer[ErrorsOrResult, Option[A], B] = {
    case Some(a) => transformer.transform(a)
    case None    => Left(List("Missing required field"))
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

    val transformer = FallibleTransformer.betweenCoproductsAccumulating[ErrorsOrResult, From, To]

    assert(transformer.transform(From.Product(Some(1))) == Right(To.Product(1)))
    assert(transformer.transform(From.Product(None)) == Left(List("Missing required field")))
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

    val transformer = FallibleTransformer.betweenCoproductsAccumulating[ErrorsOrResult, From, To]

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

    val transformer = FallibleTransformer.betweenCoproductsAccumulating[ErrorsOrResult, From, To]

    assert(transformer.transform(From.Singleton) == Right(To.Singleton))
    assert(transformer.transform(From.Product(Some(5))) == Right(To.Product(5)))
    assert(transformer.transform(From.Product(None)) == Left(List("Missing required field")))
  }

  test("Use total transformers when possible") {
    type Field = Int

    given Transformer[Field, Field] = n => n + 5

    sealed trait From
    object From {
      case class Product(field: Field) extends From
    }

    sealed trait To
    object To {
      case class Product(field: Field) extends To
    }

    val transformer = FallibleTransformer.betweenCoproductsAccumulating[ErrorsOrResult, From, To]

    assert(transformer.transform(From.Product(5)) == Right(To.Product(10)))
  }

  test("Accumulate problems") {
    sealed trait From
    object From {
      case class Product(field1: Option[Int], field2: Option[String]) extends From
    }

    sealed trait To
    object To {
      case class Product(field1: Int, field2: String) extends To
    }

    val transformer = FallibleTransformer.betweenCoproductsAccumulating[ErrorsOrResult, From, To]

    assert(transformer.transform(From.Product(None, None)) == Left(List("Missing required field", "Missing required field")))
    assert(transformer.transform(From.Product(Some(1), None)) == Left(List("Missing required field")))
    assert(transformer.transform(From.Product(None, Some("s"))) == Left(List("Missing required field")))
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
      "FallibleTransformer.betweenCoproductsAccumulating[ErrorsOrResult, From, To]"
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
      "FallibleTransformer.betweenCoproductsAccumulating[ErrorsOrResult, From, To]"
    }(
      "Neither an instance of Transformer.Fallible[ErrorsOrResult, From.Product, To.Product] was found\nnor are 'Product' 'Product' singletons with the same name"
    )
  }
}
