package io.github.arainko.ducktape

import scala.deriving.Mirror
import scala.util.NotGiven

opaque type BuilderConfig[Source, Dest] = Unit

object BuilderConfig {
  private[ducktape] def instance[Source, Dest]: BuilderConfig[Source, Dest] = ()
}

object Field {
  def const[Source, Dest, FieldType, ActualType](selector: Dest => FieldType, value: ActualType)(using
    ActualType <:< FieldType,
    Mirror.ProductOf[Source],
    Mirror.ProductOf[Dest]
  ): BuilderConfig[Source, Dest] = BuilderConfig.instance

  def computed[Source, Dest, FieldType, ActualType](selector: Dest => FieldType, f: Source => ActualType)(using
    ActualType <:< FieldType,
    Mirror.ProductOf[Source],
    Mirror.ProductOf[Dest]
  ): BuilderConfig[Source, Dest] = BuilderConfig.instance

  def renamed[Source, Dest, SourceFieldType, DestFieldType](
    destSelector: Dest => DestFieldType,
    sourceSelector: Source => SourceFieldType
  )(using
    SourceFieldType <:< DestFieldType,
    Mirror.ProductOf[Source],
    Mirror.ProductOf[Dest]
  ): BuilderConfig[Source, Dest] = BuilderConfig.instance
}

object Case {
  def const[SourceSubtype]: Case.Const[SourceSubtype] = Const.instance

  def computed[SourceSubtype]: Case.Computed[SourceSubtype] = Computed.instance

  opaque type Computed[SourceSubtype] = Unit

  object Computed {
    private[ducktape] def instance[SourceSubtype]: Computed[SourceSubtype] = ()

    extension [SourceSubtype](inst: Computed[SourceSubtype]) {
      def apply[Source, Dest](f: SourceSubtype => Dest)(using
        Mirror.SumOf[Source],
        SourceSubtype <:< Source,
        NotGiven[SourceSubtype =:= Source]
      ): BuilderConfig[Source, Dest] = BuilderConfig.instance
    }
  }

  opaque type Const[SourceSubtype] = Unit

  object Const {
    private[ducktape] def instance[SourceSubtype]: Const[SourceSubtype] = ()

    extension [SourceSubtype](inst: Const[SourceSubtype]) {
      def apply[Source, Dest](const: Dest)(using
        Mirror.SumOf[Source],
        SourceSubtype <:< Source,
        NotGiven[SourceSubtype =:= Source]
      ): BuilderConfig[Source, Dest] = BuilderConfig.instance
    }
  }
}
