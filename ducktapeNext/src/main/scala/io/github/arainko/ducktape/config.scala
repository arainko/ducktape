package io.github.arainko.ducktape

import io.github.arainko.ducktape.function.FunctionArguments

opaque type Field2[A, B] = Unit

object Field2 {
  def const[A, B, DestFieldTpe, ConstTpe](selector: Selector ?=> B => DestFieldTpe, value: ConstTpe): Field2[A, B] = ???
}

opaque type Case2[A, B] = Unit

object Case2 {
  def const[A, B, SourceTpe, FieldTpe](selector: Selector ?=> A => SourceTpe, value: FieldTpe): Case2[A, B] = ???
}

opaque type Arg[A, B, Args <: FunctionArguments] = Unit

object Arg {
  def const[A, B, Args <: FunctionArguments, DestArgTpe, ConstTpe](
    selector: Selector ?=> Args => DestArgTpe,
    value: ConstTpe
  ): Arg[A, B, Args] = ???
}
