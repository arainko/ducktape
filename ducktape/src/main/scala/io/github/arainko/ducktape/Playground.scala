package io.github.arainko.ducktape

import io.github.arainko.ducktape.internal.FallibleTransformations

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
  val p = Person(1, None, List(1, 2, 3, 1), 2)
  val srcEnum = SourceEnum.PersonCase(p)
  given Mode.Accumulating.Either[String, List] with {}

  val cos = 
    internal.CodePrinter.code:
      p.fallibleVia(Person2.apply)

  val a = 
    srcEnum
      .into[DestEnum]
      .fallible
      .transform(
        Case.const(_.at[SourceEnum.PersonCase], ???),
      )

  // internal.CodePrinter.code:
  //   costam.transform[SourceEnum, DestEnum](
  //     srcEnum,
  //     // Case.fallibleConst[SourceEnum.PersonCase](Left("" :: Nil)),



  //     Case.const(_.at[SourceEnum.PersonCase], ???),
  //   )
}
