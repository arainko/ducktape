package io.github.arainko.ducktape

import io.github.arainko.ducktape.function.FunctionArguments
import scala.deriving.Mirror

opaque type ArgBuilderConfig[Source, Dest, NamedArgs <: Tuple] = Unit

object ArgBuilderConfig {
  private[ducktape] def instance[Source, Dest, NamedArgs <: Tuple]: ArgBuilderConfig[Source, Dest, NamedArgs] = ()
}

object Arg {
  def const[Source, Dest, ArgType, ActualType, NamedArgs <: Tuple](
    selector: FunctionArguments[NamedArgs] => ArgType,
    const: ActualType
  )(using Mirror.ProductOf[Source], ActualType <:< ArgType): ArgBuilderConfig[Source, Dest, NamedArgs] = ArgBuilderConfig.instance

  def computed[Source, Dest, ArgType, ActualType, NamedArgs <: Tuple](
    selector: FunctionArguments[NamedArgs] => ArgType,
    f: Source => ActualType
  )(using Mirror.ProductOf[Source], ActualType <:< ArgType): ArgBuilderConfig[Source, Dest, NamedArgs] = ArgBuilderConfig.instance

  def renamed[Source, Dest, ArgType, FieldType, NamedArgs <: Tuple](
    destSelector: FunctionArguments[NamedArgs] => ArgType,
    sourceSelector: Source => FieldType
  )(using Mirror.ProductOf[Source], FieldType <:< ArgType): ArgBuilderConfig[Source, Dest, NamedArgs] = ArgBuilderConfig.instance

}
