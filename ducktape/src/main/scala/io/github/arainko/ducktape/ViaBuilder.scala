package io.github.arainko.ducktape

import scala.util.NotGiven
import scala.annotation.implicitNotFound
import scala.language.dynamics
import io.github.arainko.ducktape.ViaBuilder.NamedArg
import io.github.arainko.ducktape.internal.macros.*
import io.github.arainko.ducktape.Configuration.Product.*
import scala.deriving.Mirror as DerivingMirror

sealed trait ViaBuilder[
  F[_, _, _ <: Tuple, _ <: Tuple],
  From,
  To,
  NamedArgs <: Tuple,
  Config <: Tuple
] { self =>
  import ViaBuilder.*

  val constants: Map[String, Any]
  val computeds: Map[String, From => Any]

  transparent inline def withArgConst[ArgType, ConstType](
    inline selector: FunctionArgs[NamedArgs] => ArgType,
    const: ConstType
  )(using ArgType <:< ConstType) = {
    val selectedArg = SelectorMacros.selectedArg(selector)
    val withConfig = self.construct(constants = constants + (selectedArg -> const))

    BuilderMacros.withConfigEntryForArg[
      [From, To, Config <: Tuple] =>> F[From, To, NamedArgs, Config],
      From,
      To,
      Config,
      NamedArgs,
      Const
    ](withConfig, selector)
  }

  transparent inline def withArgComputed[ArgType, ComputedType](
    inline selector: FunctionArgs[NamedArgs] => ArgType,
    computed: From => ComputedType
  )(using ComputedType <:< ArgType) = {
    val selectedArg = SelectorMacros.selectedArg(selector)
    val withConfig = self.construct(computeds = computeds + (selectedArg -> computed))

    BuilderMacros.withConfigEntryForArg[
      [From, To, Config <: Tuple] =>> F[From, To, NamedArgs, Config],
      From,
      To,
      Config,
      NamedArgs,
      Computed
    ](withConfig, selector)
  }

  transparent inline def withArgRenamed[ArgType, FieldType](
    inline destSelector: FunctionArgs[NamedArgs] => ArgType,
    inline sourceSelector: From => FieldType
  )(using FieldType <:< ArgType, DerivingMirror.ProductOf[From]) =
    BuilderMacros.withConfigEntryForArgAndField[
      [From, To, Config <: Tuple] =>> F[From, To, NamedArgs, Config],
      From,
      To,
      Config,
      NamedArgs,
      Renamed
    ](self.instance, destSelector, sourceSelector)

  protected def construct(
    constants: Map[String, Any] = constants,
    computeds: Map[String, From => Any] = computeds
  ): F[From, To, NamedArgs, Config]

  protected def instance: F[From, To, NamedArgs, Config]

}

object ViaBuilder {
  infix type =!:=[A, B] = NotGiven[A =:= B]

  sealed trait NamedArg[Name <: String, Type]

  object NamedArg {
    type FindByName[Name <: String, NamedArgs <: Tuple] =
      NamedArgs match {
        case EmptyTuple               => Nothing
        case NamedArg[Name, tpe] *: _ => tpe
        case head *: tail             => FindByName[Name, tail]
      }
  }

  sealed trait FunctionArgs[NamedArgs <: Tuple] extends Dynamic {
    import NamedArg.*

    def selectDynamic(value: String)(using
      @implicitNotFound("No argument with this name found")
      ev: FindByName[value.type, NamedArgs] =!:= Nothing
    ): FindByName[value.type, NamedArgs]
  }

}

@main def run = {

  inline def cos: ViaBuilder.FunctionArgs[NamedArg["int", Int] *: NamedArg["string", String] *: EmptyTuple] => Int = _.int

  val costam =
    SelectorMacros
      .selectedArg[
        NamedArg["int", Int] *: NamedArg["string", String] *: EmptyTuple,
        Int
      ](_.int)

  println(costam)

}
