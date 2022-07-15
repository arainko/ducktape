package io.github.arainko.ducktape

import scala.deriving.Mirror
import scala.util.NotGiven

opaque type FieldConfig[Source, Dest] = Unit

def fieldConst[Source, Dest, FieldType, ActualType](selector: Dest => FieldType, value: ActualType)(using
  ActualType <:< FieldType,
  Mirror.ProductOf[Source],
  Mirror.ProductOf[Dest]
): FieldConfig[Source, Dest] = FieldConfig.instance

def fieldComputed[Source, Dest, FieldType, ActualType](selector: Dest => FieldType, f: Source => ActualType)(using
  ActualType <:< FieldType,
  Mirror.ProductOf[Source],
  Mirror.ProductOf[Dest]
): FieldConfig[Source, Dest] = FieldConfig.instance

def fieldRenamed[Source, Dest, SourceFieldType, DestFieldType](
  destSelector: Dest => DestFieldType,
  sourceSelector: Source => SourceFieldType
)(using
  SourceFieldType <:< DestFieldType,
  Mirror.ProductOf[Source],
  Mirror.ProductOf[Dest]
): FieldConfig[Source, Dest] = FieldConfig.instance

def caseConst[SourceSubtype]: FieldConfig.CaseConst[SourceSubtype] = FieldConfig.CaseConst.instance

def caseComputed[SourceSubtype]: FieldConfig.CaseComputed[SourceSubtype] = FieldConfig.CaseComputed.instance

//TODO: Move these into separate files?
object FieldConfig {
  private[ducktape] def instance[Source, Dest]: FieldConfig[Source, Dest] = ()

  opaque type CaseComputed[SourceSubtype] = Unit

  object CaseComputed {
    private[ducktape] def instance[SourceSubtype]: CaseComputed[SourceSubtype] = ()
  }

  extension [SourceSubtype] (inst: CaseComputed[SourceSubtype]) {
    def apply[Source, Dest](f: SourceSubtype => Dest)(using
      Mirror.SumOf[Source],
      SourceSubtype <:< Source,
      NotGiven[SourceSubtype =:= Source]
    ): FieldConfig[Source, Dest] = FieldConfig.instance
  }

  opaque type CaseConst[SourceSubtype] = Unit

  object CaseConst {
    private[ducktape] def instance[SourceSubtype]: CaseConst[SourceSubtype] = ()
  }

  extension [SourceSubtype] (inst: CaseConst[SourceSubtype]) {
    def apply[Source, Dest](const: Dest)(using
      Mirror.SumOf[Source],
      SourceSubtype <:< Source,
      NotGiven[SourceSubtype =:= Source]
    ): FieldConfig[Source, Dest] = FieldConfig.instance
  }

}
