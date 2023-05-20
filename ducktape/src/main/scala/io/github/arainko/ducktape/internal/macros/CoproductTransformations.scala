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
            materializedConfig(fullName) match {
              case Coproduct.Computed(tpe, function) =>
                val value = tpe match {
                  case '[tpe] =>
                    '{
                      val casted = $sourceValue.asInstanceOf[tpe]
                      $function(casted)
                    }
                }
                IfBranch(IsInstanceOf(sourceValue, source.tpe), value)

              case Coproduct.Const(tpe, value) =>
                IfBranch(IsInstanceOf(sourceValue, source.tpe), value)
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
      val cond = IsInstanceOf(sourceValue, source.tpe)

      (source.tpe -> dest.tpe) match {
        case '[src] -> '[dest] =>
          val value =
            source
              .transformerTo(dest)
              .map {
                case '{ $t: Transformer[src, dest] } =>
                  '{
                    val castedSource = $sourceValue.asInstanceOf[src]
                    ${ LiftTransformation.liftTransformation(t, 'castedSource) }
                  }
              }
              .orElse(dest.materializeSingleton)
              .getOrElse(Failure.emit(Failure.CannotMaterializeSingleton(dest.tpe)))
          IfBranch(cond, value)
      }
    }
  }

  private def ifStatement(using Quotes)(branches: List[IfBranch]): quotes.reflect.Term = {
    import quotes.reflect.*

    branches match {
      case IfBranch(cond, value) :: xs =>
        If(cond.asTerm, value.asTerm, ifStatement(xs))
      case Nil =>
        '{ throw RuntimeException("Unhandled condition encountered during Coproduct Transformer derivation") }.asTerm
    }
  }

  private def IsInstanceOf(value: Expr[Any], tpe: Type[?])(using Quotes) =
    tpe match {
      case '[tpe] => '{ $value.isInstanceOf[tpe] }
    }

  private case class IfBranch(cond: Expr[Boolean], value: Expr[Any])
}
