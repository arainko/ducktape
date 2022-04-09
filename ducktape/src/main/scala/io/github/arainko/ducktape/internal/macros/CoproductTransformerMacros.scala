package io.github.arainko.ducktape.internal.macros

import scala.quoted.*
import io.github.arainko.ducktape.Configuration.*
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

    val ValReference(ordinalRef, ordinalStatement) = '{ val ordinal = $A.ordinal($sourceValue) }
    val ordinal = ordinalRef.asExprOf[Int]
    val ifBranches = singletonIfBranches[A, B](sourceValue, ordinal, sourceCases, destCases)
    Block(ordinalStatement :: Nil, ifStatement(ifBranches)).asExprOf[B]
  }

  def transformConfigured[A: Type, B: Type, Config <: Tuple: Type](
    sourceValue: Expr[A],
    builder: Expr[Builder[?, A, B, Config]],
    A: DerivingMirror.SumOf[A],
    B: DerivingMirror.SumOf[B]
  ): Expr[B] = {
    val sourceCases = Case.fromMirror(A)
    val destCases = Case.fromMirror(B).map(c => c.name -> c).toMap
    val config = materializeCoproductConfig[Config]
    val (nonConfiguredCases, configuredCases) = sourceCases.partition(c => !config.exists(_.tpe =:= c.tpe)) //TODO: Optimize

    val ValReference(ordinalRef, ordinalStatement) = '{ val ordinal = $A.ordinal($sourceValue) }
    val ordinal = ordinalRef.asExprOf[Int]
    val nonConfiguredIfBranches = singletonIfBranches[A, B](sourceValue, ordinal, sourceCases, destCases)

    val configuredIfBranches =
      configuredCases.map { source =>
        val cond = '{ $ordinal == ${ Expr(source.ordinal) } }
        val value = '{ $builder.caseInstances($ordinal)($sourceValue) }
        cond.asTerm -> value.asTerm
      }

    Block(ordinalStatement :: Nil, ifStatement(nonConfiguredIfBranches ++ configuredIfBranches)).asExprOf[B]
  }

  private def singletonIfBranches[A: Type, B: Type](
    sourceValue: Expr[A],
    ordinalExpr: Expr[Int],
    sourceCases: List[Case],
    destinationCaseMapping: Map[String, Case]
  ) = {
    sourceCases.map { source =>
      source -> destinationCaseMapping
        .get(source.name)
        .getOrElse(report.errorAndAbort(s"No child named '${source.name}' in ${TypeRepr.of[B].show}"))
    }.map { (source, dest) =>
      val cond = '{ $ordinalExpr == ${ Expr(source.ordinal) } }
      cond.asTerm -> dest.materializeSingleton
        .getOrElse(report.errorAndAbort(s"Cannot materialize singleton. Seems like ${dest.tpe.show} is not a singleton type."))
    }
  }

  private def ifStatement(branches: List[(Term, Term)]): Term = {
    branches match
      case (p1, a1) :: xs =>
        If(p1, a1, ifStatement(xs))
      case Nil =>
        '{ throw RuntimeException("Unhandled condition encountered during Coproduct Transformer derivation") }.asTerm
  }

  private object ValReference {
    def unapply(statement: Expr[Unit]) = statement.asTerm match {
      case Inlined(_, _, Block((valDef: Statement) :: Nil, _)) => Some(Ref(valDef.symbol) -> valDef)
      case other                                               => report.errorAndAbort("Not a val reference!")
    }
  }
}

private[ducktape] object CoproductTransformerMacros {
  inline def transform[A, B](source: A)(using
    A: DerivingMirror.SumOf[A],
    B: DerivingMirror.SumOf[B]
  ): B = ${ transformMacro[A, B]('source, 'A, 'B) }

  inline def transformWithBuilder[A, B, Config <: Tuple](source: A, builder: Builder[?, A, B, Config])(using
    A: DerivingMirror.SumOf[A],
    B: DerivingMirror.SumOf[B]
  ): B =
    ${ transformWithBuilderMacro[A, B, Config]('source, 'builder, 'A, 'B) }

  def transformMacro[A: Type, B: Type](
    source: Expr[A],
    A: Expr[DerivingMirror.SumOf[A]],
    B: Expr[DerivingMirror.SumOf[B]]
  )(using Quotes): Expr[B] = CoproductTransformerMacros().transform(source, A, B)

  def transformWithBuilderMacro[A: Type, B: Type, Config <: Tuple: Type](
    source: Expr[A],
    builder: Expr[Builder[?, A, B, Config]],
    A: Expr[DerivingMirror.SumOf[A]],
    B: Expr[DerivingMirror.SumOf[B]]
  )(using Quotes): Expr[B] = CoproductTransformerMacros().transformConfigured(source, builder, A, B)
}
