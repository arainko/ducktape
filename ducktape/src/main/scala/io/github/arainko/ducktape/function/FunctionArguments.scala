package io.github.arainko.ducktape.function

import scala.annotation.implicitNotFound
import scala.util.NotGiven

sealed trait FunctionArguments[NamedArgs <: Tuple] extends Selectable {
  def selectDynamic(value: String): NamedArgument.FindByName[value.type, NamedArgs]
}
