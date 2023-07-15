package io.github.arainko.ducktape.internal.macros

import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.fallible.Mode
import io.github.arainko.ducktape.function.*

import scala.deriving.Mirror
import scala.quoted.*

private[ducktape] object Transformations {
  inline def via[Source, Dest, Func](source: Source, inline function: Func)(using
    Source: Mirror.ProductOf[Source],
    Func: FunctionMirror.Aux[Func, Dest]
  ): Dest = ${ ProductTransformations.via('source, 'function, 'Func, 'Source) }

  inline def viaConfigured[Source, Dest, Func, ArgSelector <: FunctionArguments](
    source: Source,
    inline function: Func,
    inline config: ArgBuilderConfig[Source, Dest, ArgSelector]*
  )(using Source: Mirror.ProductOf[Source]): Dest =
    ${ ProductTransformations.viaConfigured[Source, Dest, Func, ArgSelector]('source, 'function, 'config, 'Source) }

  inline def accumulatingVia[F[+x], Source, Func](inline function: Func)(using
    Func: FunctionMirror[Func]
  )(source: Source)(using Source: Mirror.ProductOf[Source], F: Mode.Accumulating[F]): F[Func.Return] = ${
    AccumulatingProductTransformations.via[F, Source, Func.Return, Func]('source, 'function, 'Source, 'F)
  }

  inline def accumulatingViaConfigured[F[+x], Source, Dest, Func, ArgSelector <: FunctionArguments](
    source: Source,
    inline function: Func,
    inline config: FallibleArgBuilderConfig[F, Source, Dest, ArgSelector] | ArgBuilderConfig[Source, Dest, ArgSelector]*
  )(using F: Mode.Accumulating[F], Source: Mirror.ProductOf[Source]): F[Dest] = ${
    AccumulatingProductTransformations.viaConfigured[F, Source, Dest, Func, ArgSelector]('source, 'function, 'config, 'Source, 'F)
  }

  inline def failFastVia[F[+x], Source, Func](inline function: Func)(using
    Func: FunctionMirror[Func]
  )(source: Source)(using Source: Mirror.ProductOf[Source], F: Mode.FailFast[F]): F[Func.Return] = ${
    FailFastProductTransformations.via[F, Source, Func.Return, Func]('source, 'function, 'Source, 'F)
  }

  inline def failFastViaConfigured[F[+x], Source, Dest, Func, ArgSelector <: FunctionArguments](
    source: Source,
    inline function: Func,
    inline config: FallibleArgBuilderConfig[F, Source, Dest, ArgSelector] | ArgBuilderConfig[Source, Dest, ArgSelector]*
  )(using F: Mode.FailFast[F], Source: Mirror.ProductOf[Source]): F[Dest] = ${
    FailFastProductTransformations.viaConfigured[F, Source, Dest, Func, ArgSelector]('source, 'function, 'config, 'Source, 'F)
  }

  inline def liftFromTransformer[Source, Dest](source: Source)(using inline transformer: Transformer[Source, Dest]) =
    ${ LiftTransformation.liftTransformation[Source, Dest]('transformer, 'source) }

  inline def transformAccumulatingConfigured[F[+x], Source, Dest](
    source: Source,
    inline config: FallibleBuilderConfig[F, Source, Dest] | BuilderConfig[Source, Dest]*
  )(using F: Mode.Accumulating[F], Source: Mirror.ProductOf[Source], Dest: Mirror.ProductOf[Dest]) = ${
    AccumulatingProductTransformations.transformConfigured[F, Source, Dest]('Source, 'Dest, 'F, 'config, 'source)
  }

  inline def transformFailFastConfigured[F[+x], Source, Dest](
    source: Source,
    inline config: FallibleBuilderConfig[F, Source, Dest] | BuilderConfig[Source, Dest]*
  )(using F: Mode.FailFast[F], Source: Mirror.ProductOf[Source], Dest: Mirror.ProductOf[Dest]) = ${
    FailFastProductTransformations.transformConfigured[F, Source, Dest]('Source, 'Dest, 'F, 'config, 'source)
  }

  inline def transformConfigured[Source, Dest](source: Source, inline config: BuilderConfig[Source, Dest]*) =
    ${ transformConfiguredMacro[Source, Dest]('source, 'config) }

  private def transformConfiguredMacro[Source: Type, Dest: Type](
    sourceValue: Expr[Source],
    config: Expr[Seq[BuilderConfig[Source, Dest]]]
  )(using Quotes): Expr[Dest] =
    mirrorOf[Source]
      .zip(mirrorOf[Dest])
      .collect {
        case '{ $source: Mirror.ProductOf[Source] } -> '{ $dest: Mirror.ProductOf[Dest] } =>
          ProductTransformations.transformConfigured(sourceValue, config, source, dest)
        case '{ $source: Mirror.SumOf[Source] } -> '{ $dest: Mirror.SumOf[Dest] } =>
          CoproductTransformations.transformConfigured(sourceValue, config, source, dest)
      }
      .getOrElse(
        quotes.reflect.report
          .errorAndAbort("Configured transformations are supported for Product -> Product and Coproduct -> Coproduct.")
      )

  inline def transformConfiguredFallible[F[+x], Source, Dest](
    source: Source,
    inline configs: FallibleBuilderConfig[F, Source, Dest] | BuilderConfig[Source, Dest]*
  )(using F: Transformer.Mode[F], Source: Mirror.Of[Source], Dest: Mirror.Of[Dest]) =
    ${ transformConfiguredFallibleMacro('source, 'configs, 'F, 'Source, 'Dest) }

  private def transformConfiguredFallibleMacro[F[+x]: Type, Source: Type, Dest: Type](
    sourceValue: Expr[Source],
    configs: Expr[Seq[FallibleBuilderConfig[F, Source, Dest] | BuilderConfig[Source, Dest]]],
    F: Expr[Transformer.Mode[F]],
    Source: Expr[Mirror.Of[Source]],
    Dest: Expr[Mirror.Of[Dest]]
  )(using Quotes): Expr[F[Dest]] =
    (F, Source, Dest) match {
      case (
            '{ $mode: Transformer.Mode.Accumulating[F] },
            '{ $source: Mirror.ProductOf[Source] },
            '{ $dest: Mirror.ProductOf[Dest] }
          ) =>
        AccumulatingProductTransformations.transformConfigured(source, dest, mode, configs, sourceValue)
      case (
            '{ $mode: Transformer.Mode.FailFast[F] },
            '{ $source: Mirror.ProductOf[Source] },
            '{ $dest: Mirror.ProductOf[Dest] }
          ) =>
        FailFastProductTransformations.transformConfigured(source, dest, mode, configs, sourceValue)
      case ('{ $mode: Transformer.Mode[F] }, '{ $source: Mirror.SumOf[Source] }, '{ $dest: Mirror.SumOf[Dest] }) =>
        FallibleCoproductTransformations.transformConfigured(source, dest, mode, configs, sourceValue)
      case _ =>
        quotes.reflect.report.errorAndAbort("""ducktape was not able to determine the exact mode of fallible transformations.
          |Make sure you have an instance of either Transformer.Mode.Accumulating[F] or Transformer.Mode.FailFast[F] (and not its supertype Transformer.Mode[F]) in implicit scope.
          |""".stripMargin)
    }

  private def mirrorOf[A: Type](using Quotes) = Expr.summon[Mirror.Of[A]]
}
