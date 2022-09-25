package io.github.arainko.ducktape

import io.github.arainko.ducktape.function.FunctionArguments
import io.github.arainko.ducktape.internal.NotQuotedException

import scala.annotation.{ compileTimeOnly, implicitNotFound }
import scala.deriving.Mirror

opaque type ArgBuilderConfig[Source, Dest, ArgSelector <: FunctionArguments] = Unit

object Arg {

  @compileTimeOnly("'Arg.const' needs to be erased from the AST with a macro.")
  def const[Source, Dest, ArgType, ActualType, ArgSelector <: FunctionArguments](
    selector: ArgSelector => ArgType,
    const: ActualType
  )(using
    @implicitNotFound("Arg.const is only supported for product types but ${Source} is not a product type.")
    ev1: Mirror.ProductOf[Source],
    ev2: ActualType <:< ArgType
  ): ArgBuilderConfig[Source, Dest, ArgSelector] = throw NotQuotedException("Arg.const")

  @compileTimeOnly("'Arg.computed' needs to be erased from the AST with a macro.")
  def computed[Source, Dest, ArgType, ActualType, ArgSelector <: FunctionArguments](
    selector: ArgSelector => ArgType,
    f: Source => ActualType
  )(using
    @implicitNotFound("Arg.computed is only supported for product types but ${Source} is not a product type.")
    ev1: Mirror.ProductOf[Source],
    ev2: ActualType <:< ArgType
  ): ArgBuilderConfig[Source, Dest, ArgSelector] = throw NotQuotedException("Arg.computed")

  @compileTimeOnly("'Arg.renamed' needs to be erased from the AST with a macro.")
  def renamed[Source, Dest, ArgType, FieldType, ArgSelector <: FunctionArguments](
    destSelector: ArgSelector => ArgType,
    sourceSelector: Source => FieldType
  )(using
    @implicitNotFound("Arg.renamed is only supported for product types but ${Source} is not a product type.")
    ev1: Mirror.ProductOf[Source],
    ev2: FieldType <:< ArgType
  ): ArgBuilderConfig[Source, Dest, ArgSelector] = throw NotQuotedException("Arg.renamed")

}
