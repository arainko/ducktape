package io.github.arainko.ducktape.function

import scala.annotation.implicitNotFound
import scala.language.dynamics
import scala.util.NotGiven

infix type =!:=[A, B] = NotGiven[A =:= B]

sealed trait FunctionArguments[NamedArgs <: Tuple] extends Dynamic {
  import NamedArgument.*

  def selectDynamic(value: String)(using
    @implicitNotFound("No argument with this name found")
    ev: FindByName[value.type, NamedArgs] =!:= Nothing
  ): FindByName[value.type, NamedArgs]
}