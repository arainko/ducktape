package io.github.arainko.ducktape

import scala.util.NotGiven
import scala.annotation.implicitNotFound
import scala.language.dynamics
import io.github.arainko.ducktape.function.*
import io.github.arainko.ducktape.internal.macros.*
import io.github.arainko.ducktape.Configuration.Product.*
import scala.deriving.Mirror as DerivingMirror
import scala.compiletime.summonInline
import io.github.arainko.ducktape.ViaBuilder.Applied

sealed trait ViaBuilder[
  F[_, _, _, _ <: Tuple, _ <: Tuple],
  From,
  To,
  Func,
  NamedArgs <: Tuple,
  Config <: Tuple
] { self =>
  val constants: Map[String, Any]
  val computeds: Map[String, From => Any]
  val function: Func

  transparent inline def withArgConst[ArgType, ConstType](
    inline selector: FunctionArguments[NamedArgs] => ArgType,
    const: ConstType
  )(using ArgType <:< ConstType) = {
    val selectedArg = SelectorMacros.selectedArg(selector)
    val withConfig = self.construct(constants = constants + (selectedArg -> const))

    BuilderMacros.withConfigEntryForArg[
      [From, To, Config <: Tuple] =>> F[From, To, Func, NamedArgs, Config],
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
      [From, To, Config <: Tuple] =>> F[From, To, Func, NamedArgs, Config],
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
      [From, To, Config <: Tuple] =>> F[From, To, Func, NamedArgs, Config],
      From,
      To,
      Config,
      NamedArgs,
      Renamed
    ](self.instance, destSelector, sourceSelector)

  protected def construct(
    constants: Map[String, Any] = constants,
    computeds: Map[String, From => Any] = computeds
  ): F[From, To, Func, NamedArgs, Config]

  protected def instance: F[From, To, Func, NamedArgs, Config]

}

object ViaBuilder {
  transparent inline def applied[From, Func](
    sourceValue: From,
    inline function: Func
  )(using From: DerivingMirror.ProductOf[From], Func: FunctionMirror[Func]) = {
    val initial = Applied[From, Func.Return, Func, Nothing, EmptyTuple](sourceValue, function, Map.empty, Map.empty)
    FunctionMacros.namedArguments[
      Func,
      [NamedArgs <: Tuple] =>> Applied[From, Func.Return, Func, NamedArgs, EmptyTuple]
    ](function, initial)
  }

  transparent inline def definition[From, Func](
    inline function: Func
  )(using From: DerivingMirror.ProductOf[From], Func: FunctionMirror[Func]) = {
    val initial = Definition[From, Func.Return, Func, Nothing, EmptyTuple](function, Map.empty, Map.empty)
    FunctionMacros.namedArguments[
      Func,
      [NamedArgs <: Tuple] =>> Definition[From, Func.Return, Func, NamedArgs, EmptyTuple]
    ](function, initial)
  }

  opaque type DefinitionViaPartiallyApplied[From] = Unit

  object DefinitionViaPartiallyApplied {
    def apply[From]: DefinitionViaPartiallyApplied[From] = ()

    extension [From](partial: DefinitionViaPartiallyApplied[From]) {
      transparent inline def apply[Func](
        inline function: Func
      )(using From: DerivingMirror.ProductOf[From], Func: FunctionMirror[Func]) =
        ViaBuilder.definition[From, Func](function)
    }
  }

  final case class Applied[From, To, Func, NamedArgs <: Tuple, Config <: Tuple] private[ViaBuilder] (
    private val sourceValue: From,
    function: Func,
    constants: Map[String, Any],
    computeds: Map[String, From => Any]
  )(using From: DerivingMirror.ProductOf[From], Func: FunctionMirror.Aux[Func, ?, To])
      extends ViaBuilder[Applied, From, To, Func, NamedArgs, Config] { self =>

    override protected def construct(
      constants: Map[String, Any] = constants,
      computeds: Map[String, From => Any] = computeds
    ): Applied[From, To, Func, NamedArgs, Config] =
      self.copy(constants = constants, computeds = computeds)

    override protected def instance: Applied[From, To, Func, NamedArgs, Config] = self

    inline def transform: To =
      ProductTransformerMacros.viaWithBuilder(sourceValue, self)(using summonInline, Func)
  }

  final case class Definition[From, To, Func, NamedArgs <: Tuple, Config <: Tuple] private[ViaBuilder] (
    function: Func,
    constants: Map[String, Any],
    computeds: Map[String, From => Any]
  )(using From: DerivingMirror.ProductOf[From], Func: FunctionMirror.Aux[Func, ?, To])
      extends ViaBuilder[Definition, From, To, Func, NamedArgs, Config] { self =>

    override protected def construct(
      constants: Map[String, Any] = constants,
      computeds: Map[String, From => Any] = computeds
    ): Definition[From, To, Func, NamedArgs, Config] =
      self.copy(constants = constants, computeds = computeds)

    override protected def instance: Definition[From, To, Func, NamedArgs, Config] = self

    inline def build: Transformer[From, To] =
      ProductTransformerMacros.viaWithBuilder(_, self)(using summonInline, Func)
  }
}
