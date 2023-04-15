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

    val ifBranches = singletonIfBranches[Source, Dest](sourceValue, Cases.source.value)
    ifStatement(ifBranches).asExprOf[Dest]
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

    val (nonConfiguredCases, configuredCases) =
      Cases.source.value.partition(c => !materializedConfig.contains(c.tpe.fullName))

    val nonConfiguredIfBranches = singletonIfBranches[Source, Dest](sourceValue, nonConfiguredCases)

    val configuredIfBranches =
      configuredCases
        .map(c => c.tpe.fullName -> c)
        .toMap
        .map {
          case (fullName, source) =>
            ConfiguredCase(materializedConfig(fullName), source)
        }
        .map {
          case ConfiguredCase(config, source) =>
            config match {
              case Coproduct.Computed(tpe, function) =>
                val cond = source.tpe match {
                  case '[tpe] => '{ $sourceValue.isInstanceOf[tpe] }
                }
                val castedSource = tpe match {
                  case '[tpe] => '{ $sourceValue.asInstanceOf[tpe] }
                }
                val value = '{ $function($castedSource) }
                cond.asTerm -> value.asTerm

              case Coproduct.Const(tpe, value) =>
                val cond = source.tpe match {
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
  )(using Quotes, Cases.Dest) = {
    import quotes.reflect.*

    sourceCases.map { source =>
      source -> Cases.dest
        .get(source.name)
        .getOrElse(Failure.emit(Failure.NoChildMapping(source.name, summon[Type[Dest]])))
    }.map { (source, dest) =>
      val cond = source.tpe match {
        case '[tpe] => '{ $sourceValue.isInstanceOf[tpe] }
      }

      cond.asTerm ->
        dest.materializeSingleton
          .getOrElse(Failure.emit(Failure.CannotMaterializeSingleton(dest.tpe)))
    }
  }

  private def ifStatement(using Quotes)(branches: List[(quotes.reflect.Term, quotes.reflect.Term)]): quotes.reflect.Term = {
    import quotes.reflect.*

    branches match {
      case (p1, a1) :: xs =>
        If(p1, a1, ifStatement(xs))
      case Nil =>
        '{ throw RuntimeException("Unhandled condition encountered during Coproduct Transformer derivation") }.asTerm
    }
  }

  private case class ConfiguredCase(config: Coproduct, subcase: Case)
}
