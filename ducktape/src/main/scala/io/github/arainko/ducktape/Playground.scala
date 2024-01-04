package io.github.arainko.ducktape

import io.github.arainko.ducktape.Plan2.Fallible

sealed trait Plan2[+E <: Plan2.Error, +F <: Plan2.Fallible[E]]

object Plan2 {
  case class Upcast() extends Plan2[Nothing, Nothing]
  case class Configured() extends Plan2[Nothing, Nothing]
  case class FallibleConfigured() extends Plan2[Nothing, Plan2.Fallible[Nothing]]
  case class BetweenProducts[+E <: Plan2.Error, +F <: Plan2.Fallible[E]](fields: List[Plan2[E, F]]) extends Plan2[E, F]
  case class Error() extends Plan2[Plan2.Error, Nothing]
  case class Fallible[+E <: Plan2.Error]() extends Plan2[E, Plan2.Fallible[E]]

}

object Playground extends App {
  val fallible: Plan2[Nothing, Plan2.Fallible[Nothing]] = ???
  val fallibleAndErroneous: Plan2[Plan2.Error, Plan2.Fallible[Nothing]] = ???
  val fallibleAndErroneous2: Plan2[Plan2.Error, Plan2.Fallible[Plan2.Error]] = ???
  val normal: Plan2[Nothing, Nothing] = ???
  val erroneousNotFallible: Plan2[Plan2.Error, Nothing] = Plan2.BetweenProducts(Nil)

  transparent inline def refineErrors(plan: Plan2[Plan2.Error, Plan2.Fallible[Plan2.Error]]): Plan2[Nothing, Plan2.Fallible[Nothing]] = 
    inline plan match {
      case p: Plan2[Nothing, Nothing] => ??? : Plan2[Nothing, Nothing]
      case p: Plan2[Plan2.Error, Nothing] => ??? : Plan2[Nothing, Nothing]
      case p: Plan2[Plan2.Error, Plan2.Fallible[Nothing]] => ??? : Plan2[Nothing, Plan2.Fallible[Nothing]]
      case p: Plan2[Plan2.Error, Plan2.Fallible[Plan2.Error]] => ??? : Plan2[Nothing, Plan2.Fallible[Nothing]]
    } 

  // val cos: Plan2[Nothing, Fallible[Nothing]] = refineErrors[Nothing](erroneousNotFallible)

  val cos2 = refineErrors(erroneousNotFallible)

  // normal match
  //   case Upcast() =>
  //   case io.github.arainko.ducktape.Plan.Error() =>
  //   case Fallible() =>

  // normal match
  //   case Upcast() =>
  //   case Configured() =>
  //   case FallibleConfigured() =>
  //   case BetweenProducts(fields) =>
  //   case io.github.arainko.ducktape.Plan.Error() =>
  //   case Fallible() =>

  // fallibleAndErroneous match
  //   case Upcast() =>
  //   case Configured() =>
  //   case FallibleConfigured() =>
  //   case BetweenProducts(fields) =>
  //   case io.github.arainko.ducktape.Plan.Error() =>
  //   case Fallible() =>

  // fallible match
  //   case Plan2.Upcast() =>
  //   case Plan2.Configured() =>
  //   case Plan2.FallibleConfigured() =>
  //   case Plan2.BetweenProducts(fields) =>
  //   case Plan2.Error() =>
  //   case Plan2.Fallible() =>

  // erroneousNotFallible match
  //   case Plan2.Upcast()     =>
  //   case Plan2.Configured() =>
  //   // case FallibleConfigured() =>
  //   case Plan2.BetweenProducts(fields)            =>
  //   case Plan2.Error() =>
  //   // case Fallible() =>

}
