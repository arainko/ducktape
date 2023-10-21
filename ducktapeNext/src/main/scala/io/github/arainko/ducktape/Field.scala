package io.github.arainko.ducktape

import scala.annotation.compileTimeOnly

// Kept around for source compat with 0.1.x
inline def Arg: Field.type = Field

opaque type Field[A, B] = Unit

object Field {
  @compileTimeOnly("Field.const is only useable as a field configuration for transformations")
  def const[A, B, DestFieldTpe, ConstTpe](selector: Selector ?=> B => DestFieldTpe, value: ConstTpe): Field[A, B] = ???

  @compileTimeOnly("Field.computed is only useable as a field configuration for transformations")
  def computed[A, B, DestFieldTpe, ComputedTpe](
    selector: Selector ?=> B => DestFieldTpe,
    function: A => ComputedTpe
  ): Field[A, B] = ???

  @compileTimeOnly("Field.renamed is only useable as a field configuration for transformations")
  def renamed[A, B, DestFieldTpe, SourceFieldTpe](
    destSelector: Selector ?=> B => DestFieldTpe,
    sourceSelector: A => SourceFieldTpe
  ): Field[A, B] = ???

  @compileTimeOnly("Field.allMatching is only useable as a field configuration for transformations")
  def allMatching[A, B, DestFieldTpe, ProductTpe](selector: Selector ?=> B => DestFieldTpe, product: ProductTpe): Field[A, B] =
    ???
}
