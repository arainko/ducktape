package io.github.arainko.ducktape

import io.github.arainko.ducktape.function.FunctionArguments
import scala.deriving.Mirror
import scala.annotation.implicitNotFound
import scala.annotation.compileTimeOnly

opaque type ArgBuilderConfig[Source, Dest, ArgSelector <: FunctionArguments] = Unit

object ArgBuilderConfig {
  private[ducktape] def instance[Source, Dest, ArgSelector <: FunctionArguments]: ArgBuilderConfig[Source, Dest, ArgSelector] = ()
}

//TODO: Slap a @compileTimeOnly on all things here
object Arg {

  def const[Source, Dest, ArgType, ActualType, ArgSelector <: FunctionArguments](
    selector: ArgSelector => ArgType,
    const: ActualType
  )(using
    @implicitNotFound("Arg.const is only supported for product types but ${Source} is not a product type.")
    ev1: Mirror.ProductOf[Source],
    ev2: ActualType <:< ArgType
  ): ArgBuilderConfig[Source, Dest, ArgSelector] = ArgBuilderConfig.instance

  def computed[Source, Dest, ArgType, ActualType, ArgSelector <: FunctionArguments](
    selector: ArgSelector => ArgType,
    f: Source => ActualType
  )(using
    @implicitNotFound("Arg.computed is only supported for product types but ${Source} is not a product type.")
    ev1: Mirror.ProductOf[Source],
    ev2: ActualType <:< ArgType
  ): ArgBuilderConfig[Source, Dest, ArgSelector] = ArgBuilderConfig.instance

  def renamed[Source, Dest, ArgType, FieldType, ArgSelector <: FunctionArguments](
    destSelector: ArgSelector => ArgType,
    sourceSelector: Source => FieldType,
  )(using
    @implicitNotFound("Arg.renamed is only supported for product types but ${Source} is not a product type.")
    ev1: Mirror.ProductOf[Source],
    ev2: FieldType <:< ArgType
  ): ArgBuilderConfig[Source, Dest, ArgSelector] = ArgBuilderConfig.instance

}
