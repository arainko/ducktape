package io.github.arainko.ducktape

import io.github.arainko.ducktape.function.FunctionArguments
import scala.deriving.Mirror
import scala.annotation.implicitNotFound

opaque type ArgBuilderConfig[Source, Dest, ArgSelector <: FunctionArguments[_]] = Unit

object ArgBuilderConfig {
  private[ducktape] def instance[Source, Dest, ArgSelector <: FunctionArguments[_]]: ArgBuilderConfig[Source, Dest, ArgSelector] = ()
}

object Arg {
  def const[Source, Dest, ArgType, ActualType, ArgSelector <: FunctionArguments[_]](
    selector: ArgSelector => ArgType,
    const: ActualType
  )(using
    @implicitNotFound("Arg.const is only supported for product types but ${Source} is not a product type.")
    ev: Mirror.ProductOf[Source]
  ): ArgBuilderConfig[Source, Dest, ArgSelector] = ArgBuilderConfig.instance

  // def computed[Source, Dest, ArgType, ActualType, ArgSelector <: FunctionArguments[_]](
  //   selector: ArgSelector => ArgType,
  //   f: Source => ActualType
  // )(using
  //   @implicitNotFound("Arg.computed is only supported for product types but ${Source} is not a product type.")
  //   ev: Mirror.ProductOf[Source]
  // ): ArgBuilderConfig[Source, Dest, ArgSelector] = ArgBuilderConfig.instance

  // def renamed[Source, Dest, ArgType, FieldType, ArgSelector <: FunctionArguments[_]](
  //   destSelector: ArgSelector => ArgType,
  //   sourceSelector: Source => FieldType
  // )(using
  //   @implicitNotFound("Arg.renamed is only supported for product types but ${Source} is not a product type.")
  //   ev: Mirror.ProductOf[Source]
  // ): ArgBuilderConfig[Source, Dest, ArgSelector] = ArgBuilderConfig.instance

}
