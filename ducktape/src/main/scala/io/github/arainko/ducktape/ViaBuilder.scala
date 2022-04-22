package io.github.arainko.ducktape

import scala.util.NotGiven
import scala.annotation.implicitNotFound
import scala.language.dynamics
import io.github.arainko.ducktape.function.*
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
  val constants: Map[String, Any]
  val computeds: Map[String, From => Any]

  transparent inline def withArgConst[ArgType, ConstType](
    inline selector: FunctionArguments[NamedArgs] => ArgType,
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
    inline selector: FunctionArguments[NamedArgs] => ArgType,
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
    inline destSelector: FunctionArguments[NamedArgs] => ArgType,
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
  transparent inline def applied[From, Func](
    sourceValue: From,
    inline function: Func
  )(using Func: FunctionMirror[Func]) = {
    val initial = Applied[From, Func.Return, Nothing, EmptyTuple](sourceValue, Map.empty, Map.empty)
    FunctionMacros.namedArguments[
      Func,
      [NamedArgs <: Tuple] =>> Applied[From, Func.Return, NamedArgs, EmptyTuple]
    ](function, initial)
  }

  final case class Applied[From, To, NamedArgs <: Tuple, Config <: Tuple] private[ViaBuilder] (
    private val sourceValue: From,
    constants: Map[String, Any],
    computeds: Map[String, From => Any]
  ) extends ViaBuilder[Applied, From, To, NamedArgs, Config] { self =>

    override protected def construct(
      constants: Map[String, Any] = constants,
      computeds: Map[String, From => Any] = computeds
    ): Applied[From, To, NamedArgs, Config] =
      self.copy(constants = constants, computeds = computeds)

    override protected def instance: Applied[From, To, NamedArgs, Config] = self
  }
}

@main def run = {
  final case class Costam(int: Int, str: String)

  val from = Costam(1, "")
  def func(str: String, int: Int, int2: Int, int3: Int) = s"$str $int"

  // val cos = FunctionMacros.namedArguments[
  //   (String, Int) => String,
  //   [NamedArgs <: Tuple] =>> NamedArgs
  // ](func, ???)

  val cos: FunctionArguments[NamedArgument["int", Int] *: EmptyTuple] = ???


  DebugMacros.structure {
    cos.int
  }

  val c = cos.int

  val builder = ViaBuilder
    .applied(from, func)
    // .withArgRenamed(_.int, _.int)
    

  println(builder)
}
