package io.github.arainko.ducktape

opaque type Field2[A, B] = Unit

object Field2 {
  def const[A, B, DestFieldTpe, ConstTpe](selector: Selector ?=> B => DestFieldTpe, value: ConstTpe): Field2[A, B] = ???

  def computed[A, B, DestFieldTpe, ComputedTpe](selector: Selector ?=> B => DestFieldTpe, function: A => ComputedTpe): Field2[A, B] = ???

  def allMatching[A, B, DestFieldTpe, ProductTpe](selector: Selector ?=> B => DestFieldTpe, product: ProductTpe): Field2[A, B] = ???
}

opaque type Case2[A, B] = Unit

object Case2 {
  def const[A, B, SourceTpe, FieldTpe](selector: Selector ?=> A => SourceTpe, value: FieldTpe): Case2[A, B] = ???
}

opaque type Arg2[A, B, Args <: FunctionArguments] = Unit

object Arg2 {
  def const[A, B, Args <: FunctionArguments, DestArgTpe, ConstTpe](
    selector: Selector ?=> Args => DestArgTpe,
    value: ConstTpe
  ): Arg2[A, B, Args] = ???
}
