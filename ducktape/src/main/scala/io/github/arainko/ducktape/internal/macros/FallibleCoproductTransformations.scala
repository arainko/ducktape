package io.github.arainko.ducktape.internal.macros

import io.github.arainko.ducktape.fallible.Mode
import io.github.arainko.ducktape.fallible.FallibleTransformer
import io.github.arainko.ducktape.function.FunctionArguments
import io.github.arainko.ducktape.internal.modules.MaterializedConfiguration.*
import io.github.arainko.ducktape.internal.modules.{ Field, * }

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

    val ifBranches = coproductBranches[F, Source, Dest](sourceValue, Cases.source.value, F)
    ifStatement(ifBranches).asExprOf[F[Dest]]
  }

  private def coproductBranches[F[+x]: Type, Source: Type, Dest: Type](
    sourceValue: Expr[Source],
    sourceCases: List[Case],
    F: Expr[Mode[F]]
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
            source.fallibleTransformerTo[F](dest).map {
              case '{ $transformer: FallibleTransformer[F, src, dest] } =>
                '{
                  val castedSource = $sourceValue.asInstanceOf[src]
                  val t = $transformer
                  t.transform(castedSource)
                }.asExprOf[F[Dest]]
            } match {
              case Right(value) => value
              case Left(explanation) =>
                dest.materializeSingleton.map { singleton =>
                  '{ $F.pure[dest]($singleton.asInstanceOf[dest]) }
                }
                  .getOrElse(
                    Failure.emit(
                      Failure.CannotTransformCoproductCaseFallible(summon[Type[F]], source.tpe, dest.tpe, explanation)
                    )
                  )
            }
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
