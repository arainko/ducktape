package io.github.arainko.ducktape

import scala.deriving.Mirror
import scala.util.NotGiven
import scala.annotation.implicitNotFound

opaque type BuilderConfig[Source, Dest] = Unit

object BuilderConfig {
  private[ducktape] def instance[Source, Dest]: BuilderConfig[Source, Dest] = ()
}

//TODO: Slap a @compileTimeOnly on all things here
object Field {
  def const[Source, Dest, FieldType, ActualType](selector: Dest => FieldType, value: ActualType)(using
    ev1: ActualType <:< FieldType,
    @implicitNotFound("Field.const is supported for product types only, but ${Source} is not a product type.")
    ev2: Mirror.ProductOf[Source],
    @implicitNotFound("Field.const is supported for product types only, but ${Dest} is not a product type.")
    ev3: Mirror.ProductOf[Dest]
  ): BuilderConfig[Source, Dest] = BuilderConfig.instance

  def computed[Source, Dest, FieldType, ActualType](selector: Dest => FieldType, f: Source => ActualType)(using
    ev1: ActualType <:< FieldType,
    @implicitNotFound("Field.computed is supported for product types only, but ${Source} is not a product type.")
    ev2: Mirror.ProductOf[Source],
    @implicitNotFound("Field.computed is supported for product types only, but ${Dest} is not a product type.")
    ev3: Mirror.ProductOf[Dest]
  ): BuilderConfig[Source, Dest] = BuilderConfig.instance

  def renamed[Source, Dest, SourceFieldType, DestFieldType](
    destSelector: Dest => DestFieldType,
    sourceSelector: Source => SourceFieldType
  )(using
    ev1: SourceFieldType <:< DestFieldType,
    @implicitNotFound("Field.renamed is supported for product types only, but ${Source} is not a product type.")
    ev2: Mirror.ProductOf[Source],
    @implicitNotFound("Field.renamed is supported for product types only, but ${Dest} is not a product type.")
    ev3: Mirror.ProductOf[Dest]
  ): BuilderConfig[Source, Dest] = BuilderConfig.instance
}

//TODO: Slap a @compileTimeOnly on all things here
object Case {
  def const[SourceSubtype]: Case.Const[SourceSubtype] = Const.instance

  def computed[SourceSubtype]: Case.Computed[SourceSubtype] = Computed.instance

  opaque type Computed[SourceSubtype] = Unit

  object Computed {
    private[ducktape] def instance[SourceSubtype]: Computed[SourceSubtype] = ()

    extension [SourceSubtype](inst: Computed[SourceSubtype]) {
      def apply[Source, Dest](f: SourceSubtype => Dest)(using
        @implicitNotFound("Case.computed is only supported for coproducts but ${Source} is not a coproduct.")
        ev1: Mirror.SumOf[Source],
        ev2: SourceSubtype <:< Source,
        @implicitNotFound("Case.computed is only supported for subtypes of ${Source}.")
        ev3: NotGiven[SourceSubtype =:= Source]
      ): BuilderConfig[Source, Dest] = BuilderConfig.instance
    }
  }

  opaque type Const[SourceSubtype] = Unit

  object Const {
    private[ducktape] def instance[SourceSubtype]: Const[SourceSubtype] = ()

    extension [SourceSubtype](inst: Const[SourceSubtype]) {
      def apply[Source, Dest](const: Dest)(using
        @implicitNotFound("Case.computed is only supported for coproducts but ${Source} is not a coproduct.")
        ev1: Mirror.SumOf[Source],
        ev2: SourceSubtype <:< Source,
        @implicitNotFound("Case.instance is only supported for subtypes of ${Source}.")
        ev3: NotGiven[SourceSubtype =:= Source]
      ): BuilderConfig[Source, Dest] = BuilderConfig.instance
    }
  }
}
