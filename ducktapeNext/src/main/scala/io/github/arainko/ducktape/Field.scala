package io.github.arainko.ducktape

opaque type Config[A, B] = Unit

object Field2 {
  def const[A, B, DestFieldTpe, ConstTpe](selector: Selector ?=> B => DestFieldTpe, value: ConstTpe): Config[A, B] = ???
}

object Case2 {
  def const[A, B, SourceTpe, FieldTpe](selector: Selector ?=> A => SourceTpe, value: FieldTpe): Config[A, B] = ???
}
