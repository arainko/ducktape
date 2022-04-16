package io.github.arainko.ducktape

import scala.language.dynamics
import scala.util.NotGiven
import scala.annotation.implicitNotFound

final case class ViaBuilder[From, To, NamedArgs <: Tuple, Config <: Tuple](
  val constants: Map[String, Any],
  val computeds: Map[String, From => Any]
) {

  def withFieldConst[FieldType, ConstType](
    selector: ViaBuilder.Selector[NamedArgs] => FieldType,
    const: ConstType
  )(using FieldType <:< ConstType) = ???
}

object ViaBuilder {
  import scala.compiletime.ops.string.*

  sealed trait NamedArg[Name <: String, Type]

  sealed trait FindError[Msg <: String]

  type Err[Msg]

  object NamedArg {
    type FindByName[Name <: String, NamedArgs <: Tuple] =
      NamedArgs match {
        case EmptyTuple               => Nothing
        case NamedArg[Name, tpe] *: _ => tpe
        case head *: tail             => FindByName[Name, tail]
      }
  }

  sealed trait Selector[NamedArgs <: Tuple] extends Dynamic {
    def selectDynamic(value: String)(using
      @implicitNotFound("No such field found in the function")
      ev: NotGiven[NamedArg.FindByName[value.type, NamedArgs] =:= Nothing]
    ): NamedArg.FindByName[value.type, NamedArgs]
  }

  val builder =
    ViaBuilder[
      String,
      Int,
      NamedArg["int", Int] *: NamedArg["string", String] *: EmptyTuple,
      EmptyTuple
    ](Map.empty, Map.empty)

  builder.withFieldConst(_.int, 1)

}
