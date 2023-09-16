package io.github.arainko.ducktape

sealed trait FunctionArguments extends Selectable {
  def selectDynamic(value: String): Nothing
}
