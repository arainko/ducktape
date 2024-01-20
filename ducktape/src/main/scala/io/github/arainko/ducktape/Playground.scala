package io.github.arainko.ducktape

import io.github.arainko.ducktape.internal.FallibleTransformations

case class Person(int: Int, opt: Option[Int], list: List[Int], normal: Int)
case class Person2(int: RefinedInt, opt: Option[RefinedInt], list: Vector[RefinedInt], normal: Int, extra: Option[RefinedInt])

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

class Costam[F[+x], M <: Mode[F]](using F: M) {
  inline def transform[A, B](value: A, inline config: Field.Fallible[F, A, B] | Case.Fallible[F, A, B]*): F[B] =
    FallibleTransformations.between[F, A, B](value, F, config*)
}


object Playground extends App {
  val p = Person(1, None, List(1, 2, 3, 1), 2)
  val srcEnum = SourceEnum.PersonCase(p)
  given Mode.FailFast.Either[List[String]] with {}

  val costam = new Costam

  costam.transform[SourceEnum, DestEnum](
    srcEnum,
    Case.fallibleConst(_.at[SourceEnum.PersonCase], Right(???))
  )



  summon[Left[List[String], Nothing] <:< Either[List[String], DestEnum]]

  def left[A](value: A): Left[A, Nothing] = Left(value)

  // val left = Left("" :: Nil)

  // val res =
  //   // internal.CodePrinter.code:
  //     FallibleTransformations.between[[a] =>> Either[List[String], a], SourceEnum, DestEnum](
  //       srcEnum,
  //       mode, 
  //       // Field.fallibleConst(_.at[DestEnum.PersonCase].p.extra, RefinedInt.transformer.transform(0)),
  //       Field.fallibleComputed(_.at[DestEnum.PersonCase], a => Right(???) : Either[Nothing, Nothing])
  //     )


  // println(res)

//   println(res)
}
