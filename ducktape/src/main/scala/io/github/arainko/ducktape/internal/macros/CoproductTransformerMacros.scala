package io.github.arainko.ducktape.internal.macros

import scala.quoted.*
import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.internal.modules.*
import scala.deriving.*

private[ducktape] class CoproductTransformerMacros(using val quotes: Quotes)
    extends Module,
      CaseModule,
      FieldModule,
      SelectorModule,
      MirrorModule,
      ConfigurationModule {
  import quotes.reflect.*
  import MaterializedConfiguration.*

  def transform[Source: Type, Dest: Type](
    sourceValue: Expr[Source],
    Source: Expr[Mirror.SumOf[Source]],
    Dest: Expr[Mirror.SumOf[Dest]]
  ): Expr[Dest] = {
    given Cases.Source = Cases.Source.fromMirror(Source)
    given Cases.Dest = Cases.Dest.fromMirror(Dest)
    val ifBranches = singletonIfBranches[Source, Dest](sourceValue, Cases.source.value)
    ifStatement(ifBranches).asExprOf[Dest]
  }

  def transformConfigured[Source: Type, Dest: Type](
    sourceValue: Expr[Source],
    config: Expr[Seq[BuilderConfig[Source, Dest]]],
    Source: Expr[Mirror.SumOf[Source]],
    Dest: Expr[Mirror.SumOf[Dest]]
  ): Expr[Dest] = {
    given Cases.Source = Cases.Source.fromMirror(Source)
    given Cases.Dest = Cases.Dest.fromMirror(Dest)
    val materializedConfig = MaterializedConfiguration.materializeCoproductConfig(config)

    val (nonConfiguredCases, configuredCases) =
      Cases.source.value.partition(c => !materializedConfig.exists(_.tpe =:= c.tpe))

    val nonConfiguredIfBranches = singletonIfBranches[Source, Dest](sourceValue, nonConfiguredCases)

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

    ifStatement(nonConfiguredIfBranches ++ configuredIfBranches).asExprOf[Dest]
  }

  private def singletonIfBranches[Source: Type, Dest: Type](
    sourceValue: Expr[Source],
    sourceCases: List[Case]
  )(using Cases.Dest) = {
    sourceCases.map { source =>
      source -> Cases.dest
        .get(source.name)
        .getOrElse(report.errorAndAbort(s"No child named '${source.name}' in ${TypeRepr.of[Dest].show}"))
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
  inline def transform[Source, Dest](source: Source)(using
    Source: Mirror.SumOf[Source],
    Dest: Mirror.SumOf[Dest]
  ): Dest = ${ transformMacro[Source, Dest]('source, 'Source, 'Dest) }

  def transformMacro[Source: Type, Dest: Type](
    source: Expr[Source],
    Source: Expr[Mirror.SumOf[Source]],
    Dest: Expr[Mirror.SumOf[Dest]]
  )(using Quotes): Expr[Dest] = CoproductTransformerMacros().transform(source, Source, Dest)

  inline def transformConfigured[Source, Dest](source: Source, inline config: BuilderConfig[Source, Dest]*)(using
    Source: Mirror.SumOf[Source],
    Dest: Mirror.SumOf[Dest]
  ): Dest =
    ${ transformConfiguredMacro[Source, Dest]('source, 'config, 'Source, 'Dest) }

  def transformConfiguredMacro[Source: Type, Dest: Type](
    source: Expr[Source],
    config: Expr[Seq[BuilderConfig[Source, Dest]]],
    Source: Expr[Mirror.SumOf[Source]],
    Dest: Expr[Mirror.SumOf[Dest]]
  )(using Quotes): Expr[Dest] = CoproductTransformerMacros().transformConfigured(source, config, Source, Dest)
}
