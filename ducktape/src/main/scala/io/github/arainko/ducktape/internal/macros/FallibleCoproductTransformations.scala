package io.github.arainko.ducktape.internal.macros

import io.github.arainko.ducktape.Transformer
import io.github.arainko.ducktape.fallible.{ FallibleTransformer, Mode }
import io.github.arainko.ducktape.internal.modules.*

import scala.deriving.*
import scala.quoted.*
import io.github.arainko.ducktape.BuilderConfig
import io.github.arainko.ducktape.FallibleBuilderConfig
import io.github.arainko.ducktape.internal.modules.MaterializedConfiguration.FallibleCoproduct
import io.github.arainko.ducktape.internal.modules.MaterializedConfiguration.FallibleCoproduct.Computed
import io.github.arainko.ducktape.internal.modules.MaterializedConfiguration.FallibleCoproduct.Const
import io.github.arainko.ducktape.internal.modules.MaterializedConfiguration.FallibleCoproduct.Total

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

    ExhaustiveCoproductMatching(cases = Cases.source, sourceValue = sourceValue, ifNoMatch = ifNoMatch) { subtypeCase =>
      coproductBranch(sourceValue, subtypeCase, F)
    }
  }

  def transformConfigured[F[+x]: Type, Source: Type, Dest: Type](
    Source: Expr[Mirror.SumOf[Source]],
    Dest: Expr[Mirror.SumOf[Dest]],
    F: Expr[Mode[F]],
    config: Expr[Seq[BuilderConfig[Source, Dest] | FallibleBuilderConfig[F, Source, Dest]]],
    sourceValue: Expr[Source]
  )(using Quotes): Expr[F[Dest]] = {
    import quotes.reflect.*

    given Cases.Source = Cases.Source.fromMirror(Source)
    given Cases.Dest = Cases.Dest.fromMirror(Dest)

    val materializedConfig =
      MaterializedConfiguration.FallibleCoproduct
        .fromFallibleCaseConfig(config)
        .map(c => c.tpe.fullName -> c)
        .toMap

    val ifNoMatch = '{ throw RuntimeException("Unhandled condition encountered during Coproduct Transformer derivation") }

    ExhaustiveCoproductMatching(cases = Cases.source, sourceValue = sourceValue, ifNoMatch = ifNoMatch) { subtypeCase =>
      materializedConfig.get(subtypeCase.tpe.fullName) match {
        case Some(FallibleCoproduct.Total(value)) => ???
        case Some(FallibleCoproduct.Const(tpe, value)) => 
          value.asExprOf[F[Dest]]
        case Some(FallibleCoproduct.Computed(tpe, function)) => 
          tpe match {
            case '[tpe] =>
              '{
                val casted = $sourceValue.asInstanceOf[tpe]
                $function(casted)
              }.asExprOf[F[Dest]]
            }
        case None => coproductBranch(sourceValue, subtypeCase, F)
      }
    }
  }

  private def coproductBranch[F[+x]: Type, Source: Type, Dest: Type](
    sourceValue: Expr[Source],
    source: Case,
    F: Expr[Mode[F]]
  )(using Quotes, Cases.Dest): Expr[F[Dest]] = {
    import quotes.reflect.*

    val dest = Cases.dest.getOrElse(source.name, Failure.emit(Failure.NoChildMapping(source.name, summon[Type[Dest]])))

    // that weird error where when you don't name the Quotes param it does not compile here...
    def tryFallibleTransformation(using q: Quotes) = 
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

    def trySingletonTransformation(using Quotes) =
      dest.materializeSingleton.map { singleton =>
        '{ $F.pure[Dest]($singleton.asInstanceOf[Dest]) }
      }

    tryFallibleTransformation match {
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
