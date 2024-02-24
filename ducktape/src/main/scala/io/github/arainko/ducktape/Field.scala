package io.github.arainko.ducktape

import scala.annotation.compileTimeOnly

// Kept around for source compat with 0.1.x
def Arg: Field.type = Field

opaque type Field[A, B] <: Field.Fallible[Nothing, A, B] = Field.Fallible[Nothing, A, B]

object Field {
  opaque type Fallible[+F[+x], A, B] = Unit

  @compileTimeOnly("Field.fallibleConst is only useable as a field configuration for transformations")
  def fallibleConst[F[+x], A, B, DestFieldTpe](
    selector: Selector ?=> B => DestFieldTpe,
    value: F[DestFieldTpe]
  ): Field.Fallible[F, A, B] = ???

  @compileTimeOnly("Field.fallibleComputed is only useable as a field configuration for transformations")
  def fallibleComputed[F[+x], A, B, DestFieldTpe](
    selector: Selector ?=> B => DestFieldTpe,
    function: A => F[DestFieldTpe]
  ): Field.Fallible[F, A, B] = ???

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

  @compileTimeOnly("Field.fallbackToNone is only useable as a field configuration for transformations")
  def fallbackToNone[A, B]: Field[A, B] & Regional = ???

  @compileTimeOnly("Field.fallbackToDefault is only useable as a field configuration for transformations")
  def fallbackToDefault[A, B]: Field[A, B] & Regional = ???

  @compileTimeOnly("Field.allMatching is only useable as a field configuration for transformations")
  def allMatching[A, B, DestFieldTpe, ProductTpe](selector: Selector ?=> B => DestFieldTpe, product: ProductTpe): Field[A, B] =
    ???

  @compileTimeOnly("Field.allMatching is only useable as a field configuration for transformations")
  def allMatching[A, B, ProductTpe](product: ProductTpe): Field[A, B] =
    ???
}
