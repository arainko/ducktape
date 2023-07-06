package io.github.arainko.ducktape.internal.macros

import io.github.arainko.ducktape.Transformer
import io.github.arainko.ducktape.fallible.{ FallibleTransformer, Mode }
import io.github.arainko.ducktape.function.FunctionArguments
import io.github.arainko.ducktape.internal.modules.MaterializedConfiguration.*
import io.github.arainko.ducktape.internal.modules.*

import scala.deriving.*
import scala.quoted.*

private[ducktape] object FallibleCoproductTransformations {

  def transform[F[+x]: Type, Source: Type, Dest: Type](
    Source: Expr[Mirror.SumOf[Source]],
    Dest: Expr[Mirror.SumOf[Dest]],
    F: Expr[Mode[F]],
    sourceValue: Expr[Source]
  )(using Quotes): Expr[F[Dest]] = {
    given Cases.Source = Cases.Source.fromMirror(Source)
    given Cases.Dest = Cases.Dest.fromMirror(Dest)

    val ifNoMatch = '{ throw RuntimeException("Unhandled condition encountered during Coproduct Transformer derivation") }

    ExhaustiveCoproductMatching(cases = Cases.source, sourceValue = sourceValue, ifNoMatch = ifNoMatch) {
      coproductBranch(sourceValue, _, F)
    }
  }

  private def coproductBranch[F[+x]: Type, Source: Type, Dest: Type](
    sourceValue: Expr[Source],
    source: Case,
    F: Expr[Mode[F]]
  )(using Quotes, Cases.Dest): Expr[F[Dest]] = {
    import quotes.reflect.*

    val dest = Cases.dest.getOrElse(source.name, Failure.emit(Failure.NoChildMapping(source.name, summon[Type[Dest]])))

    def tryTotalTransformation: Either[String, Expr[F[Dest]]] =
      source.transformerTo(dest).map {
        case '{ $total: Transformer[src, dest] } =>
          '{
            val castedSource = $sourceValue.asInstanceOf[src]
            val transformed = ${ LiftTransformation.liftTransformation(total, 'castedSource) }
            $F.pure(transformed)
          }.asExprOf[F[Dest]]
      }

    def tryFallibleTransformation: Either[String, Expr[F[Dest]]] =
      source.fallibleTransformerTo[F](dest).map {
        case '{ FallibleTransformer.fallibleFromTotal[F, src, dest](using $total, $support) } =>
          '{
            val castedSource = $sourceValue.asInstanceOf[src]
            val transformed = ${ LiftTransformation.liftTransformation(total, 'castedSource) }
            $F.pure(transformed)
          }.asExprOf[F[Dest]]

        case '{ $transformer: FallibleTransformer[F, src, dest] } =>
          val castedSource: Expr[src] = '{ $sourceValue.asInstanceOf[src] }
          '{ $transformer.transform($castedSource) }.asExprOf[F[Dest]]
      }

    def trySingletonTransformation: Option[Expr[F[Dest]]] =
      dest.materializeSingleton.map { singleton =>
        '{ $F.pure[Dest]($singleton.asInstanceOf[Dest]) }
      }

    tryTotalTransformation
      .orElse(tryFallibleTransformation) match {
      case Right(expr)       => expr
      case Left(explanation) =>
        // note: explanation is information from the FallibleTransformation implicit search.
        trySingletonTransformation
          .getOrElse(
            Failure.emit(Failure.CannotTransformCoproductCaseFallible(summon[Type[F]], source.tpe, dest.tpe, explanation))
          )
    }
  }
}
