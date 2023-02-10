package io.github.arainko.ducktape

import io.github.arainko.ducktape.internal.NotQuotedException

import scala.annotation.{ compileTimeOnly, implicitNotFound }
import scala.deriving.Mirror
import scala.util.NotGiven

opaque type BuilderConfig[Source, Dest] = Unit

object Field {

  @compileTimeOnly("'Field.const' needs to be erased from the AST with a macro.")
  def const[Source, Dest, FieldType, ActualType](selector: Dest => FieldType, value: ActualType)(using
    ev1: ActualType <:< FieldType,
    @implicitNotFound("Field.const is supported for product types only, but ${Source} is not a product type.")
    ev2: Mirror.ProductOf[Source],
    @implicitNotFound("Field.const is supported for product types only, but ${Dest} is not a product type.")
    ev3: Mirror.ProductOf[Dest]
  ): BuilderConfig[Source, Dest] = throw NotQuotedException("Field.const")

  @compileTimeOnly("'Field.computed' needs to be erased from the AST with a macro.")
  def computed[Source, Dest, FieldType, ActualType](selector: Dest => FieldType, f: Source => ActualType)(using
    ev1: ActualType <:< FieldType,
    @implicitNotFound("Field.computed is supported for product types only, but ${Source} is not a product type.")
    ev2: Mirror.ProductOf[Source],
    @implicitNotFound("Field.computed is supported for product types only, but ${Dest} is not a product type.")
    ev3: Mirror.ProductOf[Dest]
  ): BuilderConfig[Source, Dest] = throw NotQuotedException("Field.computed")

  @compileTimeOnly("'Field.renamed' needs to be erased from the AST with a macro.")
  def renamed[Source, Dest, SourceFieldType, DestFieldType](
    destSelector: Dest => DestFieldType,
    sourceSelector: Source => SourceFieldType
  )(using
    ev1: SourceFieldType <:< DestFieldType,
    @implicitNotFound("Field.renamed is supported for product types only, but ${Source} is not a product type.")
    ev2: Mirror.ProductOf[Source],
    @implicitNotFound("Field.renamed is supported for product types only, but ${Dest} is not a product type.")
    ev3: Mirror.ProductOf[Dest]
  ): BuilderConfig[Source, Dest] = throw NotQuotedException("Field.renamed")

  @compileTimeOnly("'Field.allMatching' needs to be erased from the AST with a macro.")
  def allMatching[Source, Dest, FieldSource](
    fieldSource: FieldSource
  )(using
    @implicitNotFound("Field.allMatching is supported for product types only, but ${Source} is not a product type.")
    ev1: Mirror.ProductOf[Source],
    @implicitNotFound("Field.allMatching is supported for product types only, but ${Dest} is not a product type.")
    ev2: Mirror.ProductOf[Dest],
    @implicitNotFound("Field.allMatching is supported for product types only, but ${FieldSource} is not a product type.")
    ev3: Mirror.ProductOf[FieldSource]
  ): BuilderConfig[Source, Dest] = throw NotQuotedException("Field.allMatching")
}

object Case {

  @compileTimeOnly("'Case.const' needs to be erased from the AST with a macro.")
  def const[SourceSubtype]: Case.Const[SourceSubtype] = throw NotQuotedException("Case.const")

  @compileTimeOnly("'Case.computed' needs to be erased from the AST with a macro.")
  def computed[SourceSubtype]: Case.Computed[SourceSubtype] = throw NotQuotedException("Case.computed")

  opaque type Computed[SourceSubtype] = Unit

  object Computed {
    extension [SourceSubtype](inst: Computed[SourceSubtype]) {

      @compileTimeOnly("'Case.computed' needs to be erased from the AST with a macro.")
      def apply[Source, Dest](f: SourceSubtype => Dest)(using
        @implicitNotFound("Case.computed is only supported for coproducts but ${Source} is not a coproduct.")
        ev1: Mirror.SumOf[Source],
        ev2: SourceSubtype <:< Source,
        @implicitNotFound("Case.computed is only supported for subtypes of ${Source}.")
        ev3: NotGiven[SourceSubtype =:= Source]
      ): BuilderConfig[Source, Dest] = throw NotQuotedException("Case.computed")
    }
  }

  opaque type Const[SourceSubtype] = Unit

  object Const {
    extension [SourceSubtype](inst: Const[SourceSubtype]) {

      @compileTimeOnly("'Case.const' needs to be erased from the AST with a macro.")
      def apply[Source, Dest](const: Dest)(using
        @implicitNotFound("Case.computed is only supported for coproducts but ${Source} is not a coproduct.")
        ev1: Mirror.SumOf[Source],
        ev2: SourceSubtype <:< Source,
        @implicitNotFound("Case.instance is only supported for subtypes of ${Source}.")
        ev3: NotGiven[SourceSubtype =:= Source]
      ): BuilderConfig[Source, Dest] = throw NotQuotedException("Case.const")
    }
  }
}
