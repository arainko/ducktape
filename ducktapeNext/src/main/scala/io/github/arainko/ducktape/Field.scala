package io.github.arainko.ducktape

opaque type Config[A, B] = Unit

object Field2 {
  def const[A, B, FieldTpe](selector: Selector.Of[B], value: FieldTpe): Config[A, B] = ???
}

object Case2 {
  def const[A, B, FieldTpe](selector: Selector.Of[A], value: FieldTpe): Config[A, B] = ???
}
