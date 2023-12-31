package io.github.arainko.ducktape

import scala.annotation.compileTimeOnly

// Kept around for source compat with 0.1.x
def Arg: Field.type = Field

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

  @compileTimeOnly("Field.default is only useable as a field configuration for transformations")
  def default[A, B, FieldType](selector: Selector ?=> B => FieldType): Field[A, B] = ???

  def fallbackToNone[A, B, FieldType](selector: Selector ?=> B => FieldType): Field[A, B] = ???

  def fallbackToNone[A, B]: Field[A, B] & Regional = ???

  def fallbackToDefault[A, B]: Field[A, B] & Regional = ???

  Regional.regional[Field, Int, Int](Field.fallbackToDefault[Int, Int])[Byte](_.toByte)
  
  @compileTimeOnly("Field.allMatching is only useable as a field configuration for transformations")
  def allMatching[A, B, DestFieldTpe, ProductTpe](selector: Selector ?=> B => DestFieldTpe, product: ProductTpe): Field[A, B] =
    ???

  @compileTimeOnly("Field.allMatching is only useable as a field configuration for transformations")
  def allMatching[A, B, ProductTpe](product: ProductTpe): Field[A, B] =
    ???
}
