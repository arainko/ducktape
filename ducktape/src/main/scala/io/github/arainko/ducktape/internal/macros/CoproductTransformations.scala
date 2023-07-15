package io.github.arainko.ducktape.internal.macros

import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.internal.modules.MaterializedConfiguration.*
import io.github.arainko.ducktape.internal.modules.*

import scala.deriving.*
import scala.quoted.*

// Ideally should live in `modules` but due to problems with ProductTransformations and LiftTransformation
// is kept here for consistency
private[ducktape] object CoproductTransformations {

  def transform[Source: Type, Dest: Type](
    sourceValue: Expr[Source],
    Source: Expr[Mirror.SumOf[Source]],
    Dest: Expr[Mirror.SumOf[Dest]]
  )(using Quotes): Expr[Dest] = {
    given Cases.Source = Cases.Source.fromMirror(Source)
    given Cases.Dest = Cases.Dest.fromMirror(Dest)

    val ifNoMatch = '{ throw RuntimeException("Unhandled condition encountered during Coproduct Transformer derivation") }

    ExhaustiveCoproductMatching(
      Cases.source,
      sourceValue,
      ifNoMatch
    ) { c =>
      coproductBranch(c, sourceValue)
    }
  }

  def transformConfigured[Source: Type, Dest: Type](
    sourceValue: Expr[Source],
    config: Expr[Seq[BuilderConfig[Source, Dest]]],
    Source: Expr[Mirror.SumOf[Source]],
    Dest: Expr[Mirror.SumOf[Dest]]
  )(using Quotes): Expr[Dest] = {
    import quotes.reflect.*

    given Cases.Source = Cases.Source.fromMirror(Source)
    given Cases.Dest = Cases.Dest.fromMirror(Dest)
    val materializedConfig =
      MaterializedConfiguration.Coproduct
        .fromCaseConfig(config)
        .map(c => c.tpe.fullName -> c)
        .toMap

    val ifNoMatch = '{ throw RuntimeException("Unhandled condition encountered during Coproduct Transformer derivation") }

    ExhaustiveCoproductMatching(
      Cases.source,
      sourceValue,
      ifNoMatch
    ) { c =>
      materializedConfig.get(c.tpe.fullName) match {
        case Some(Coproduct.Computed(tpe, function)) =>
          tpe match {
            case '[tpe] =>
              '{
                val casted = $sourceValue.asInstanceOf[tpe]
                $function(casted)
              }.asExprOf[Dest]
          }
        case Some(Coproduct.Const(tpe, value)) => value.asExprOf[Dest]
        case None                              => coproductBranch[Source, Dest](c, sourceValue)
      }
    }
  }

  private def coproductBranch[Source: Type, Dest: Type](
    source: Case,
    sourceValue: Expr[Source]
  )(using Quotes, Cases.Dest): Expr[Dest] = {
    import quotes.reflect.*

    val dest = Cases.dest.getOrElse(source.name, Failure.emit(Failure.NoChildMapping(source.name, summon[Type[Dest]])))

    def tryTransformation(using q: Quotes) =
      source.transformerTo(dest).map {
        case '{ $t: Transformer[src, dest] } =>
          '{
            val castedSource = $sourceValue.asInstanceOf[src]
            ${ LiftTransformation.liftTransformation(t, 'castedSource) }
          }.asExprOf[Dest]
      }

    def trySingletonTransformation(using Quotes) =
      dest.materializeSingleton.map { singleton =>
        '{ $singleton.asInstanceOf[Dest] }
      }

    tryTransformation match {
      case Right(expr)       => expr
      case Left(explanation) =>
        // note: explanation is information from the Transformation implicit search.
        trySingletonTransformation
          .getOrElse(
            Failure.emit(Failure.CannotTransformCoproductCase(source.tpe, dest.tpe, explanation))
          )
    }
  }
}
