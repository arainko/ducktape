package io.github.arainko.ducktape.function

import scala.annotation.implicitNotFound
import scala.language.dynamics
import scala.util.NotGiven

sealed trait FunctionArguments[NamedArgs <: Tuple] extends Dynamic {
  def selectDynamic(value: String): NamedArgument.FindByName[value.type, NamedArgs]
}
