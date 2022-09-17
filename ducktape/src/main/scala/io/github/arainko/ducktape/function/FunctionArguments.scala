package io.github.arainko.ducktape.function

import scala.annotation.implicitNotFound
import scala.util.NotGiven

sealed trait FunctionArguments extends Selectable {
  def selectDynamic(value: String): Nothing
}
