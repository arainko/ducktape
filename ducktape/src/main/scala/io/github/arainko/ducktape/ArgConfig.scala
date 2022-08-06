package io.github.arainko.ducktape

import io.github.arainko.ducktape.function.FunctionArguments
import scala.deriving.Mirror

opaque type ArgConfig[Source, Dest, NamedArgs <: Tuple] = Unit

object ArgConfig {
  private[ducktape] def instance[Source, Dest, NamedArgs <: Tuple]: ArgConfig[Source, Dest, NamedArgs] = ()
}

object Arg {
  def const[Source, Dest, ArgType, ActualType, NamedArgs <: Tuple](
    selector: FunctionArguments[NamedArgs] => ArgType,
    const: ActualType
  )(using Mirror.ProductOf[Source], ActualType <:< ArgType): ArgConfig[Source, Dest, NamedArgs] = ArgConfig.instance

  def computed[Source, Dest, ArgType, ActualType, NamedArgs <: Tuple](
    selector: FunctionArguments[NamedArgs] => ArgType,
    f: Source => ActualType
  )(using Mirror.ProductOf[Source], ActualType <:< ArgType): ArgConfig[Source, Dest, NamedArgs] = ArgConfig.instance

  def renamed[Source, Dest, ArgType, FieldType, NamedArgs <: Tuple](
    destSelector: FunctionArguments[NamedArgs] => ArgType,
    sourceSelector: Source => FieldType
  )(using Mirror.ProductOf[Source], FieldType <:< ArgType): ArgConfig[Source, Dest, NamedArgs] = ArgConfig.instance

}
