package io.github.arainko.ducktape.internal.macros

import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.fallible.{ FallibleTransformer, Mode }

import scala.deriving.*
import scala.quoted.*

private[ducktape] object DerivedTransformers {
  inline def product[Source, Dest](using
    Source: Mirror.ProductOf[Source],
    Dest: Mirror.ProductOf[Dest]
  ): Transformer[Source, Dest] = ${ deriveProductTransformerMacro('Source, 'Dest) }

  def deriveProductTransformerMacro[Source: Type, Dest: Type](
    Source: Expr[Mirror.ProductOf[Source]],
    Dest: Expr[Mirror.ProductOf[Dest]]
  )(using Quotes): Expr[Transformer[Source, Dest]] =
    '{ source => ${ ProductTransformations.transform[Source, Dest]('source, Source, Dest) } }

  inline def coproduct[Source, Dest](using
    Source: Mirror.SumOf[Source],
    Dest: Mirror.SumOf[Dest]
  ): Transformer[Source, Dest] = ${ deriveCoproductTransformerMacro[Source, Dest]('Source, 'Dest) }

  def deriveCoproductTransformerMacro[Source: Type, Dest: Type](
    Source: Expr[Mirror.SumOf[Source]],
    Dest: Expr[Mirror.SumOf[Dest]]
  )(using Quotes): Expr[Transformer[Source, Dest]] =
    '{ source => ${ CoproductTransformations.transform[Source, Dest]('source, Source, Dest) } }

  inline def toAnyVal[Source, Dest]: Transformer[Source, Dest] =
    ${ deriveToAnyValTransformerMacro[Source, Dest] }

  def deriveToAnyValTransformerMacro[Source: Type, Dest: Type](using Quotes): Expr[Transformer[Source, Dest]] =
    '{ source => ${ ProductTransformations.transformToAnyVal('source) } }

  inline def fromAnyVal[Source, Dest] =
    ${ deriveFromAnyValTransformerMacro[Source, Dest] }

  def deriveFromAnyValTransformerMacro[Source: Type, Dest: Type](using Quotes): Expr[Transformer[Source, Dest]] =
    '{ source => ${ ProductTransformations.transformFromAnyVal('source) } }

  inline def failFastProduct[F[+x], Source, Dest](using
    F: Mode.FailFast[F],
    Source: Mirror.ProductOf[Source],
    Dest: Mirror.ProductOf[Dest]
  ): FallibleTransformer[F, Source, Dest] = ${ deriveFailFastProductTransformerMacro[F, Source, Dest]('F, 'Source, 'Dest) }

  def deriveFailFastProductTransformerMacro[F[+x]: Type, Source: Type, Dest: Type](
    F: Expr[Mode.FailFast[F]],
    Source: Expr[Mirror.ProductOf[Source]],
    Dest: Expr[Mirror.ProductOf[Dest]]
  )(using Quotes): Expr[FallibleTransformer[F, Source, Dest]] =
    '{ source => ${ FailFastProductTransformations.transform[F, Source, Dest](Source, Dest, F, 'source) } }

  inline def failFastCoproduct[F[+x], Source, Dest](using
    F: Mode.FailFast[F],
    Source: Mirror.SumOf[Source],
    Dest: Mirror.SumOf[Dest]
  ): FallibleTransformer[F, Source, Dest] =
    ${ deriveFailFastCoproductTransformerMacro[F, Source, Dest]('F, 'Source, 'Dest) }

  def deriveFailFastCoproductTransformerMacro[F[+x]: Type, Source: Type, Dest: Type](
    F: Expr[Mode.FailFast[F]],
    Source: Expr[Mirror.SumOf[Source]],
    Dest: Expr[Mirror.SumOf[Dest]]
  )(using Quotes): Expr[FallibleTransformer[F, Source, Dest]] =
    '{ source => ${ FailFastCoproductTransformations.transform[F, Source, Dest](Source, Dest, F, 'source) } }

  inline def accumulatingProduct[F[+x], Source, Dest](using
    F: Mode.Accumulating[F],
    Source: Mirror.ProductOf[Source],
    Dest: Mirror.ProductOf[Dest]
  ): FallibleTransformer[F, Source, Dest] = ${ deriveAccumulatingProductTransformerMacro[F, Source, Dest]('F, 'Source, 'Dest) }

  def deriveAccumulatingProductTransformerMacro[F[+x]: Type, Source: Type, Dest: Type](
    F: Expr[Mode.Accumulating[F]],
    Source: Expr[Mirror.ProductOf[Source]],
    Dest: Expr[Mirror.ProductOf[Dest]]
  )(using Quotes): Expr[FallibleTransformer[F, Source, Dest]] =
    '{ source => ${ AccumulatingProductTransformations.transform[F, Source, Dest](Source, Dest, F, 'source) } }

  inline def accumulatingCoproduct[F[+x], Source, Dest](using
    F: Mode.Accumulating[F],
    Source: Mirror.SumOf[Source],
    Dest: Mirror.SumOf[Dest]
  ): FallibleTransformer[F, Source, Dest] =
    ${ deriveAccumulatingCoproductTransformerMacro[F, Source, Dest]('F, 'Source, 'Dest) }

  def deriveAccumulatingCoproductTransformerMacro[F[+x]: Type, Source: Type, Dest: Type](
    F: Expr[Mode.Accumulating[F]],
    Source: Expr[Mirror.SumOf[Source]],
    Dest: Expr[Mirror.SumOf[Dest]]
  )(using Quotes): Expr[FallibleTransformer[F, Source, Dest]] =
    '{ source => ${ AccumulatingCoproductTransformations.transform[F, Source, Dest](Source, Dest, F, 'source) } }
}
