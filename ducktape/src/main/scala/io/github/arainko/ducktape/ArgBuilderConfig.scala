package io.github.arainko.ducktape

import io.github.arainko.ducktape.function.FunctionArguments
import scala.deriving.Mirror
import scala.annotation.implicitNotFound

opaque type ArgBuilderConfig[Source, Dest, NamedArgs <: Tuple] = Unit

object ArgBuilderConfig {
  private[ducktape] def instance[Source, Dest, NamedArgs <: Tuple]: ArgBuilderConfig[Source, Dest, NamedArgs] = ()
}

object Arg {
  def const[Source, Dest, ArgType, ActualType, NamedArgs <: Tuple](
    selector: FunctionArguments[NamedArgs] => ArgType,
    const: ActualType
  )(using
    @implicitNotFound("Arg.const is only supported for product types but ${Source} is not a product type.")
    ev: Mirror.ProductOf[Source]
  ): ArgBuilderConfig[Source, Dest, NamedArgs] = ArgBuilderConfig.instance

  def computed[Source, Dest, ArgType, ActualType, NamedArgs <: Tuple](
    selector: FunctionArguments[NamedArgs] => ArgType,
    f: Source => ActualType
  )(using
    @implicitNotFound("Arg.computed is only supported for product types but ${Source} is not a product type.")
    ev: Mirror.ProductOf[Source]
  ): ArgBuilderConfig[Source, Dest, NamedArgs] = ArgBuilderConfig.instance

  def renamed[Source, Dest, ArgType, FieldType, NamedArgs <: Tuple](
    destSelector: FunctionArguments[NamedArgs] => ArgType,
    sourceSelector: Source => FieldType
  )(using
    @implicitNotFound("Arg.renamed is only supported for product types but ${Source} is not a product type.")
    ev: Mirror.ProductOf[Source]
  ): ArgBuilderConfig[Source, Dest, NamedArgs] = ArgBuilderConfig.instance

}
