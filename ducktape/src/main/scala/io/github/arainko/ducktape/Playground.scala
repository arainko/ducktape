package io.github.arainko.ducktape

import io.github.arainko.ducktape.internal.FallibleTransformations
// import io.github.arainko.ducktape.fallible.*

case class Person(int: Int, opt: Option[Int], list: List[Int], normal: Int)
case class Person2(int: RefinedInt, opt: Option[RefinedInt], list: Vector[RefinedInt], normal: Int)

case class RefinedInt(value: Int)

enum SourceEnum {
  case PersonCase(p: Person)
}

enum DestEnum {
  case PersonCase(p: Person2)
}

object RefinedInt {
  given transformer: Transformer.Fallible[[A] =>> Either[List[String], A], Int, RefinedInt] with {
    def transform(source: Int): Either[List[String], RefinedInt] = 
      if (source == 0) Left("dupal" :: Nil) else Right(RefinedInt(source))
  }
}


object Playground extends App {
  type F[X] = Either[List[String], X]

  given Transformer.Mode.Accumulating[F] =
    Transformer.Mode.Accumulating.either[String, List]

  given deriveMandatoryOptionTransformer[A, B](using
    transformer: Transformer.Fallible.Derived[F, A, B]
  ): Transformer.Fallible[F, Option[A], B] =
    new {
      def transform(source: Option[A]): F[B] = 
        source match
          case Some(a) => transformer.transform(a)
          case None    => Left(List("Missing required field"))
    }

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

  val src: io.github.arainko.ducktape.Playground.From = From.Product(None)


  summon[Transformer.Fallible[F, Option[Int], Int]]

  val transformer = Transformer.Fallible.derive[F, From, To]

    // assert(transformer.transform(From.Product(Some(1))) == Right(To.Product(1)))
    // assert(transformer.transform(From.Product(None)) == Left(List("Missing required field")))
    // assert(transformer.transform(From.Product2(Some(2))) == Right(To.Product2(2)))
  
//   val p = Person(1,None, Nil, 2)
//   val srcEnum = SourceEnum.PersonCase(p)
//   given Mode.FailFast.Either[List[String]] with {}

//   val cos = 
//     internal.CodePrinter.code:
//       p.fallibleVia(Person2.apply)

  // val a = 
  //   srcEnum
  //     .into[DestEnum]
  //     .fallible
  //     .transform(
  //       Case.const(_.at[SourceEnum.PersonCase], ???),
  //     )

  // internal.CodePrinter.code:
  //   costam.transform[SourceEnum, DestEnum](
  //     srcEnum,
  //     // Case.fallibleConst[SourceEnum.PersonCase](Left("" :: Nil)),



  //     Case.const(_.at[SourceEnum.PersonCase], ???),
  //   )
}
