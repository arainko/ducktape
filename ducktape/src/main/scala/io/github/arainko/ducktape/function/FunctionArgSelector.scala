package io.github.arainko.ducktape.function

abstract class FunctionArgSelector[NamedArgs <: Tuple] extends Selectable {
  def selectDynamic(value: String): NamedArgument.FindByName[value.type, NamedArgs] 
}

@main def main = {
  val selector: FunctionArgSelector[NamedArgument["name", Int] *: EmptyTuple] {
    val name: Int
  } = ???

  val asd = selector.name
}
