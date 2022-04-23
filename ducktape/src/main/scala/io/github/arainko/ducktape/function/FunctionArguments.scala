package io.github.arainko.ducktape.function

import scala.annotation.implicitNotFound
import scala.language.dynamics
import scala.util.NotGiven

infix type =!:=[A, B] = NotGiven[A =:= B]

sealed trait FunctionArguments[NamedArgs <: Tuple] extends Dynamic {
  import NamedArgument.*

  def selectDynamic(value: String): FindByName[value.type, NamedArgs]
}