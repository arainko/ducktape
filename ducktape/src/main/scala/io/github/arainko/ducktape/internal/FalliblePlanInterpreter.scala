package io.github.arainko.ducktape.internal

import scala.quoted.*
import io.github.arainko.ducktape.Mode
import io.github.arainko.ducktape.internal.Summoner.UserDefined.TotalTransformer
import io.github.arainko.ducktape.internal.Summoner.UserDefined.FallibleTransformer
import io.github.arainko.ducktape.Transformer
import scala.collection.Factory

object FalliblePlanInterpreter {
  def run[F[+x]: Type, A: Type, B: Type](
    plan: Plan[Nothing, Fallible],
    sourceValue: Expr[A],
    mode: Expr[Mode.Accumulating[F]]
  )(using Quotes): Expr[F[B]] =
    recurse(plan, sourceValue, mode)(using sourceValue) match
      case Value.Unwrapped(value) => '{ $mode.pure(${ value.asExprOf[B] }) }
      case Value.Wrapped(value)   => value.asExprOf[F[B]]

  private def recurse[F[+x]: Type, A: Type](
    plan: Plan[Nothing, Fallible],
    value: Expr[Any],
    F: Expr[Mode.Accumulating[F]]
  )(using toplevelValue: Expr[A])(using Quotes): Value[F] = {
    import quotes.reflect.*

    // FallibilityRefiner.run(plan) match {
    //   case plan: Plan[Nothing, Nothing] =>
    //     Value.Unwrapped(PlanInterpreter.recurse[A](plan, value))
    //   case None =>
    plan match {
      case Plan.Upcast(_, _) => Value.Unwrapped(value)

      case Plan.Configured(_, _, config) => ???

      case Plan.BetweenProducts(source, dest, fieldPlans) =>
        val (unwrapped, wrapped) =
          fieldPlans.map {
            case (fieldName, p: Plan.Configured[Fallible]) =>
              (fieldName, p.dest.tpe, recurse(p, value, F))
            case (fieldName, plan) =>
              val fieldValue = value.accessFieldByName(fieldName).asExpr
              (fieldName, plan.dest.tpe, recurse(plan, fieldValue, F))
          }.partitionMap {
            case (fieldName, tpe, Value.Unwrapped(value)) =>
              Left(ZippedProduct.Field.Unwrapped(ZippedProduct.Field(fieldName, tpe), value))
            case (fieldName, tpe, Value.Wrapped(value)) =>
              Right(ZippedProduct.Field.Wrapped(ZippedProduct.Field(fieldName, tpe), value))
          }

        dest.tpe match {
          case '[dest] =>
            NonEmptyList
              .fromList(wrapped.toList)
              .map { wrappeds =>
                Value.Wrapped {
                  ZippedProduct.zipAndConstruct[F, dest](F, wrappeds, unwrapped.toList) { unwrapped =>
                    val args = unwrapped.map(field => NamedArg(field.field.name, field.value.asTerm))
                    Constructor(dest.tpe.repr).appliedToArgs(args).asExprOf[dest]
                  }
                }
              }
              .getOrElse {
                val args = unwrapped.map(field => NamedArg(field.field.name, field.value.asTerm)).toList
                Value.Unwrapped(Constructor(dest.tpe.repr).appliedToArgs(args).asExprOf[dest])
              }
        }

      case Plan.BetweenCoproducts(source, dest, casePlans) => ???

      case Plan.BetweenProductFunction(source, dest, argPlans) => ???

      case Plan.BetweenOptions(source, dest, plan) =>
        (source.paramStruct.tpe, dest.paramStruct.tpe) match {
          case '[src] -> '[dest] =>
            val source = value.asExprOf[Option[src]]
            Value.Wrapped(
              '{
                $source match
                  case None        => $F.pure(None)
                  case Some(value) => $F.map(${ recurse(plan, 'value, F).wrapped(F).asExprOf[F[dest]] }, Some.apply)
              }
            )
        }

      case Plan.BetweenNonOptionOption(source, dest, plan) =>
        source.tpe match {
          case '[src] =>
            val source = value.asExprOf[src]
            Value.Wrapped('{ $F.map(${ recurse(plan, source, F).wrapped(F) }, Some.apply) })
        }

      case Plan.BetweenCollections(source, dest, plan) =>
        (dest.tpe, source.paramStruct.tpe, dest.paramStruct.tpe) match {
          case ('[destCollTpe], '[srcElem], '[destElem]) =>
            // TODO: Make it nicer, move this into Planner since we cannot be sure that a factory exists
            val factory = Expr.summon[Factory[destElem, destCollTpe]].get
            factory match {
              case '{
                    type dest <: Iterable[`destElem`]
                    $f: Factory[`destElem`, `dest`]
                  } =>
                val sourceValue = value.asExprOf[Iterable[srcElem]]

                def transformation(value: Expr[srcElem])(using Quotes) = recurse(plan, value, F).wrapped(F).asExprOf[F[destElem]]
                Value.Wrapped('{ $F.traverseCollection[srcElem, destElem, Iterable[srcElem], dest]($sourceValue, a => ${ transformation('a) })(using $f) })
            }

        }

      case Plan.BetweenSingletons(source, dest) =>
        Value.Unwrapped(dest.value)

      case plan @ Plan.BetweenWrappedUnwrapped(_, _, _) =>
        Value.Unwrapped(PlanInterpreter.recurse(plan, value))

      case plan @ Plan.BetweenUnwrappedWrapped(_, _) =>
        Value.Unwrapped(PlanInterpreter.recurse(plan, value))

      case Plan.UserDefined(source, dest, transformer) =>
        (source.tpe -> dest.tpe) match {
          case '[src] -> '[dest] =>
            val source = value.asExprOf[src]
            transformer match
              case TotalTransformer(t) =>
                val transformer = t.asExprOf[Transformer[src, dest]]
                Value.Unwrapped('{ $transformer.transform($source) })
              case FallibleTransformer(t) =>
                val transformer = t.asExprOf[Transformer.Fallible[F, src, dest]]
                Value.Wrapped('{ $transformer.transform($source) })
        }

      case Plan.Derived(source, dest, transformer) =>
        (source.tpe -> dest.tpe) match {
          case '[src] -> '[dest] =>
            val source = value.asExprOf[src]
            transformer match
              case Summoner.Derived.TotalTransformer(t) =>
                val transformer = t.asExprOf[Transformer.Derived[src, dest]]
                Value.Unwrapped('{ $transformer.transform($source) })
              case Summoner.Derived.FallibleTransformer(t) =>
                val transformer = t.asExprOf[Transformer.Fallible.Derived[F, src, dest]]
                Value.Wrapped('{ $transformer.transform($source) })
        }
    }
    // }
  }

  private enum Value[F[+x]] {
    final def wrapped(F: Expr[Mode[F]])(using Quotes, Type[F]) =
      this match
        case Unwrapped(value) => '{ $F.pure($value) }
        case Wrapped(value)   => value

    case Unwrapped(value: Expr[Any])
    case Wrapped(value: Expr[F[Any]])
  }
}
