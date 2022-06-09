package io.github.arainko.ducktape

import scala.deriving.Mirror
import scala.util.NotGiven

opaque type FieldConfig[Source, Dest] = Unit

def const[Source, Dest, FieldType, ActualType](selector: Dest => FieldType, value: ActualType)(using
  ActualType <:< FieldType,
  Mirror.ProductOf[Source],
  Mirror.ProductOf[Dest]
): FieldConfig[Source, Dest] = FieldConfig.instance

def computed[Source, Dest, FieldType, ActualType](selector: Dest => FieldType, f: Source => ActualType)(using
  ActualType <:< FieldType,
  Mirror.ProductOf[Source],
  Mirror.ProductOf[Dest]
): FieldConfig[Source, Dest] = FieldConfig.instance

def renamed[Source, Dest, SourceFieldType, DestFieldType](
  destSelector: Dest => DestFieldType,
  sourceSelector: Source => SourceFieldType
)(using
  SourceFieldType <:< DestFieldType,
  Mirror.ProductOf[Source],
  Mirror.ProductOf[Dest]
): FieldConfig[Source, Dest] = FieldConfig.instance

def instance[SourceSubtype]: FieldConfig.Instance[SourceSubtype] = FieldConfig.Instance.instance

object FieldConfig {
  private[ducktape] val instance: FieldConfig[Nothing, Nothing] = ()

  opaque type Instance[-SourceSubtype] = Unit

  object Instance {
    // does this really need to be a val?
    private[ducktape] val instance: Instance[Any] = ()
  }

  extension [SourceSubtype] (inst: Instance[SourceSubtype]) {
    def apply[Source, Dest](f: SourceSubtype => Dest)(using
      Mirror.SumOf[Source],
      SourceSubtype <:< Source,
      NotGiven[SourceSubtype =:= Source]
    ): FieldConfig[Source, Dest] = FieldConfig.instance
  }
}
