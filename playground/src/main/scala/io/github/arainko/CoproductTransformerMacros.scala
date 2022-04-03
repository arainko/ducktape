package io.github.arainko

import scala.quoted.*
import io.github.arainko.Configuration.*
import io.github.arainko.internal.*
import scala.deriving.Mirror as DerivingMirror

class CoproductTransformerMacros(using val quotes: Quotes)
    extends Module,
      FieldModule,
      MirrorModule,
      SelectorModule,
      ConfigurationModule {
  import quotes.reflect.*
  import MaterializedConfiguration.*

  /*
    Idea:
      1. Associate by name
      2. Try to resolve singletons for destination and source types
      3. Construct one big if statement that the associated by name symbol types and matches it to the other one

      enum Enum1:
        case Case1
        case Case2
        case Case3

      enum Enum2:
        case Case1
        case Case2
        case Case3

      Enum1 to Enum2:
        def transform(value: Enum1): Enum2 =
          if (value.isInstanceOf[Enum1.Case1]) Enum2.Case1
          else if (value.isInstanceOf[Enum1.Case2]) Enum2.Case2
          else if (value.isInstanceOf[Enum1.Case3]) Enum2.Case3
          else throw new Exception("Unknown case")

   */

  def transform[A: Type, B: Type](
    sourceValue: Expr[A],
    A: DerivingMirror.SumOf[A],
    B: DerivingMirror.SumOf[B]
  ): Expr[B] = {
    val sourceCases = Case.fromMirror(A)
    val destCases = Case.fromMirror(B).map(c => c.name -> c).toMap

    val ValReference(ordinalRef, ordinalStatement) = '{ val ordinal = $A.ordinal($sourceValue) }
    val ordinal = ordinalRef.asExprOf[Int]
    val ifBranches =
      sourceCases
        .map(source => source -> destCases.get(source.name).getOrElse(report.errorAndAbort("SHITE")))
        .map { (source, dest) =>
          val cond = '{ $ordinal == ${ Expr(source.ordinal) } }
          cond.asTerm -> dest.materializeSingleton.getOrElse(report.errorAndAbort("cannot materialize singleton"))
        }

    Block(ordinalStatement :: Nil, ifStatement(ifBranches)).asExprOf[B]
  }

  def transformConfigured[A: Type, B: Type, Config <: Tuple: Type](
    sourceValue: Expr[A],
    builder: Expr[Builder[A, B, Config]],
    A: DerivingMirror.SumOf[A],
    B: DerivingMirror.SumOf[B]
  ): Expr[B] = {
    val sourceCases = Case.fromMirror(A)
    val destCases = Case.fromMirror(B).map(c => c.name -> c).toMap
    val config = materializeCoproductConfig[Config]
    val (nonConfiguredCases, configuredCases) = sourceCases.partition(c => !config.exists(_.tpe =:= c.tpe)) //TODO: Optimize

    val ValReference(ordinalRef, ordinalStatement) = '{ val ordinal = $A.ordinal($sourceValue) }
    val ordinal = ordinalRef.asExprOf[Int]
    val nonConfiguredIfBranches =
      nonConfiguredCases
        .map(source => source -> destCases.get(source.name).getOrElse(report.errorAndAbort("SHITE")))
        .map { (source, dest) =>
          val cond = '{ $ordinal == ${ Expr(source.ordinal) } }
          cond.asTerm -> dest.materializeSingleton.getOrElse(report.errorAndAbort("cannot materialize singleton"))
        }

    val configuredIfBranches =
      configuredCases.map { source =>
        val cond = '{ $ordinal == ${ Expr(source.ordinal) } }
        val value = '{ $builder.caseInstances($ordinal)($sourceValue) }
        cond.asTerm -> value.asTerm
      }

    Block(ordinalStatement :: Nil, ifStatement(nonConfiguredIfBranches ++ configuredIfBranches)).asExprOf[B]
  }

  private def ifStatement(branches: List[(Term, Term)]): Term = {
    branches match
      case (p1, a1) :: xs =>
        If(p1, a1, ifStatement(xs))
      case Nil =>
        '{ throw RuntimeException("Unhandled condition encountered during Show derivation") }.asTerm
  }

  private object ValReference {
    def unapply(statement: Expr[Unit]) = statement.asTerm match {
      case Inlined(_, _, Block((valDef: Statement) :: Nil, _)) => Some(Ref(valDef.symbol) -> valDef)
      case other                                               => report.errorAndAbort("Not a val reference!")
    }
  }
}

object CoproductTransformerMacros {
  inline def transform[A, B](source: A)(using
    A: DerivingMirror.SumOf[A],
    B: DerivingMirror.SumOf[B]
  ): B = ${ transformMacro[A, B]('source, 'A, 'B) }

  inline def transformWithBuilder[A, B, Config <: Tuple](source: A, builder: Builder[A, B, Config])(using
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
    builder: Expr[Builder[A, B, Config]],
    A: Expr[DerivingMirror.SumOf[A]],
    B: Expr[DerivingMirror.SumOf[B]]
  )(using Quotes): Expr[B] = CoproductTransformerMacros().transformConfigured(source, builder, A, B)
}
