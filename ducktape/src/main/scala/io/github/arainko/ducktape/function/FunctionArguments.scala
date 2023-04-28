package io.github.arainko.ducktape.function

sealed trait FunctionArguments extends Selectable {
  def selectDynamic(value: String): Nothing
}
