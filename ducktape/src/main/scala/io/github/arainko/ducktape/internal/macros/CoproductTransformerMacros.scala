package io.github.arainko.ducktape.internal.macros

import scala.quoted.*
import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.internal.modules.*
import scala.deriving.Mirror as DerivingMirror

private[ducktape] class CoproductTransformerMacros(using val quotes: Quotes)
    extends Module,
      FieldModule,
      MirrorModule,
      SelectorModule,
      ConfigurationModule {
  import quotes.reflect.*
  import MaterializedConfiguration.*

  def transform[A: Type, B: Type](
    sourceValue: Expr[A],
    A: DerivingMirror.SumOf[A],
    B: DerivingMirror.SumOf[B]
  ): Expr[B] = {
    val sourceCases = Case.fromMirror(A)
    val destCases = Case.fromMirror(B).map(c => c.name -> c).toMap
    val ifBranches = singletonIfBranches[A, B](sourceValue, sourceCases, destCases)
    ifStatement(ifBranches).asExprOf[B]
  }

  def transformConfigured[A: Type, B: Type](
    sourceValue: Expr[A],
    config: Expr[Seq[BuilderConfig[A, B]]],
    A: DerivingMirror.SumOf[A],
    B: DerivingMirror.SumOf[B]
  ): Expr[B] = {
    val materializedConfig = config match {
      case Varargs(config) => MaterializedConfiguration.materializeCoproductConfig(config)
      case other           => report.errorAndAbort(s"Failed to materialize field config: ${other.asTerm.show} ")
    }

    val sourceCases = Case.fromMirror(A)
    val destCases = Case.fromMirror(B).map(c => c.name -> c).toMap
    val (nonConfiguredCases, configuredCases) =
      sourceCases.partition(c => !materializedConfig.exists(_.tpe =:= c.tpe)) //TODO: Optimize

    val nonConfiguredIfBranches = singletonIfBranches[A, B](sourceValue, nonConfiguredCases, destCases)

    val configuredIfBranches =
      configuredCases.zip(materializedConfig).map { (source, config) =>
        config match {
          case Coproduct.Computed(tpe, function) =>
            val cond = source.tpe.asType match {
              case '[tpe] => '{ $sourceValue.isInstanceOf[tpe] }
            }
            val castedSource = tpe.asType match {
              case '[tpe] => '{ $sourceValue.asInstanceOf[tpe] }
            }
            val value = '{ $function($castedSource) }
            cond.asTerm -> value.asTerm

          case Coproduct.Const(tpe, value) =>
            val cond = source.tpe.asType match {
              case '[tpe] => '{ $sourceValue.isInstanceOf[tpe] }
            }
            cond.asTerm -> value.asTerm
        }
      }

    ifStatement(nonConfiguredIfBranches ++ configuredIfBranches).asExprOf[B]
  }

  private def singletonIfBranches[A: Type, B: Type](
    sourceValue: Expr[A],
    sourceCases: List[Case],
    destinationCaseMapping: Map[String, Case]
  ) = {
    sourceCases.map { source =>
      source -> destinationCaseMapping
        .get(source.name)
        .getOrElse(report.errorAndAbort(s"No child named '${source.name}' in ${TypeRepr.of[B].show}"))
    }.map { (source, dest) =>
      val cond = source.tpe.asType match {
        case '[tpe] => '{ $sourceValue.isInstanceOf[tpe] }
      }
      cond.asTerm -> dest.materializeSingleton
        .getOrElse(report.errorAndAbort(s"Cannot materialize singleton. Seems like ${dest.tpe.show} is not a singleton type."))
    }
  }

  private def ifStatement(branches: List[(Term, Term)]): Term = {
    branches match {
      case (p1, a1) :: xs =>
        If(p1, a1, ifStatement(xs))
      case Nil =>
        '{ throw RuntimeException("Unhandled condition encountered during Coproduct Transformer derivation") }.asTerm
    }
  }
}

private[ducktape] object CoproductTransformerMacros {
  inline def transform[A, B](source: A)(using
    A: DerivingMirror.SumOf[A],
    B: DerivingMirror.SumOf[B]
  ): B = ${ transformMacro[A, B]('source, 'A, 'B) }

  def transformMacro[A: Type, B: Type](
    source: Expr[A],
    A: Expr[DerivingMirror.SumOf[A]],
    B: Expr[DerivingMirror.SumOf[B]]
  )(using Quotes): Expr[B] = CoproductTransformerMacros().transform(source, A, B)

  inline def transformConfigured[A, B](source: A, inline config: BuilderConfig[A, B]*)(using
    A: DerivingMirror.SumOf[A],
    B: DerivingMirror.SumOf[B]
  ): B =
    ${ transformConfiguredMacro[A, B]('source, 'config, 'A, 'B) }

  def transformConfiguredMacro[A: Type, B: Type](
    source: Expr[A],
    config: Expr[Seq[BuilderConfig[A, B]]],
    A: Expr[DerivingMirror.SumOf[A]],
    B: Expr[DerivingMirror.SumOf[B]]
  )(using Quotes): Expr[B] = CoproductTransformerMacros().transformConfigured(source, config, A, B)
}
