package io.github.arainko.ducktape.internal

import scala.quoted.*
import io.github.arainko.ducktape.internal.Summoner.UserDefined.TotalTransformer
import io.github.arainko.ducktape.internal.Summoner.UserDefined.FallibleTransformer
import io.github.arainko.ducktape.Transformer
import scala.collection.Factory

object FalliblePlanInterpreter {
  def run[F[+x]: Type, A: Type, B: Type](
    plan: Plan[Nothing, Fallible],
    sourceValue: Expr[A],
    mode: TransformationMode[F]
  )(using Quotes): Expr[F[B]] =
    recurse(plan, sourceValue, mode)(using sourceValue) match
      case Value.Unwrapped(value) => '{ ${ mode.value }.pure[B](${ value.asExprOf[B] }) }
      case Value.Wrapped(value)   => value.asExprOf[F[B]]

  private def recurse[F[+x]: Type, A: Type](
    plan: Plan[Nothing, Fallible],
    value: Expr[Any],
    F: TransformationMode[F]
  )(using toplevelValue: Expr[A])(using Quotes): Value[F] = {
    import quotes.reflect.*

    FallibilityRefiner.run(plan) match
      case plan: Plan[Nothing, Nothing] =>
        Value.Unwrapped(PlanInterpreter.recurse[A](plan, value))
      case None =>
        plan match {
          case Plan.Upcast(_, _) => Value.Unwrapped(value)

          case Plan.Configured(_, _, config) =>
            config match
              case cfg @ Configuration.Const(_, _) =>
                Value.Unwrapped(PlanInterpreter.evaluateConfig(cfg, value))
              case cfg @ Configuration.CaseComputed(tpe, function) =>
                Value.Unwrapped(PlanInterpreter.evaluateConfig(cfg, value))
              case cfg @ Configuration.FieldComputed(tpe, function) =>
                Value.Unwrapped(PlanInterpreter.evaluateConfig(cfg, value))
              case cfg @ Configuration.FieldReplacement(source, name, tpe) =>
                Value.Unwrapped(PlanInterpreter.evaluateConfig(cfg, value))
              case Configuration.FallibleConst(value, tpe) =>
                tpe match {
                  case '[dest] =>
                    Value.Wrapped(value.asExprOf[F[dest]])
                }
              case Configuration.FallibleFieldComputed(tpe, function) =>
                tpe match {
                  case '[tpe] =>
                    Value.Wrapped('{ $function($toplevelValue) }.asExprOf[F[tpe]])
                }

              case Configuration.FallibleCaseComputed(tpe, function) =>
                tpe match {
                  case '[tpe] =>
                    Value.Wrapped('{ $function($value) }.asExprOf[F[tpe]])
                }

          case plan @ Plan.BetweenProducts(source, dest, fieldPlans) =>
            productTransformation(plan, fieldPlans, value, F)(ProductConstructor.Primary(dest))
          case Plan.BetweenCoproducts(source, dest, casePlans) =>
            dest.tpe match {
              case '[destSupertype] =>
                val branches = casePlans.map { plan =>
                  (plan.source.tpe -> plan.dest.tpe) match {
                    case '[src] -> '[dest] =>
                      val sourceValue = '{ $value.asInstanceOf[src] }
                      IfExpression.Branch(
                        IsInstanceOf(value, plan.source.tpe),
                        recurse(plan, sourceValue, F).wrapped(F, Type.of[dest])
                      )
                  }
                }.toList

                Value.Wrapped(
                  IfExpression(
                    branches,
                    '{ throw new RuntimeException("Unhandled case. This is most likely a bug in ducktape.") }
                  ).asExprOf[F[destSupertype]]
                )
            }

          case plan @ Plan.BetweenProductFunction(source, dest, argPlans) =>
            productTransformation(plan, argPlans, value, F)(ProductConstructor.Func(dest.function))

          case Plan.BetweenOptions(source, dest, plan) =>
            (source.paramStruct.tpe, dest.paramStruct.tpe) match {
              case '[src] -> '[dest] =>
                val source = value.asExprOf[Option[src]]
                Value.Wrapped(
                  '{
                    $source match
                      case None        => ${ F.value }.pure(None)
                      case Some(value) => ${ F.value }.map(${ recurse(plan, 'value, F).wrapped(F, Type.of[dest]) }, Some.apply)
                  }
                )
            }

          case Plan.BetweenNonOptionOption(source, dest, plan) =>
            (source.tpe -> dest.paramStruct.tpe) match {
              case '[src] -> '[dest] =>
                val source = value.asExprOf[src]
                Value.Wrapped('{ ${ F.value }.map(${ recurse(plan, source, F).wrapped(F, Type.of[dest]) }, Some.apply) })
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

                    def transformation(value: Expr[srcElem])(using Quotes) =
                      recurse(plan, value, F).wrapped(F, Type.of[destElem])

                    Value.Wrapped('{
                      ${ F.value }.traverseCollection[srcElem, destElem, Iterable[srcElem], dest](
                        $sourceValue,
                        a => ${ transformation('a) }
                      )(using $f)
                    })
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
  }

  private enum Value[F[+x]] {
    final def wrapped[A](F: TransformationMode[F], tpe: Type[A])(using Quotes, Type[F]): Expr[F[A]] =
      given Type[A] = tpe

      this match
        case Unwrapped(value) =>
          '{ ${ F.value }.pure[A](${ value.asExprOf[A] }) }

        case Wrapped(value) => value.asExprOf[F[A]]

    case Unwrapped(value: Expr[Any])
    case Wrapped(value: Expr[F[Any]])
  }

  private def productTransformation[F[+x]: Type, A: Type](
    plan: Plan[Nothing, Fallible],
    fieldPlans: Map[String, Plan[Nothing, Fallible]],
    value: Expr[Any],
    F: TransformationMode[F]
  )(construct: ProductConstructor)(using quotes: Quotes, toplevelValue: Expr[A]) = {
    import quotes.reflect.*

    val (unwrapped, wrapped) =
      fieldPlans.map {
        case (fieldName, p: Plan.Configured[Fallible]) =>
          (fieldName, p.dest.tpe, recurse(p, value, F))
        case (fieldName, plan) =>
          val fieldValue = value.accessFieldByName(fieldName).asExpr
          (fieldName, plan.dest.tpe, recurse(plan, fieldValue, F))
      }.partitionMap {
        case (fieldName, tpe, Value.Unwrapped(value)) =>
          Left(ProductZipper.Field.Unwrapped(ProductZipper.Field(fieldName, tpe), value))
        case (fieldName, tpe, Value.Wrapped(value)) =>
          Right(ProductZipper.Field.Wrapped(ProductZipper.Field(fieldName, tpe), value))
      }

    plan.dest.tpe match {
      case '[dest] =>
        F match {
          case TransformationMode.Accumulating(f) =>
            NonEmptyList
              .fromList(wrapped.toList)
              .map { wrappeds =>
                Value.Wrapped {
                  ProductZipper.zipAndConstruct[F, dest](f, wrappeds, unwrapped.toList)(construct)
                }
              }
              .getOrElse {
                val fields = unwrapped.map(field => field.field.name -> field.value).toMap
                Value.Unwrapped(construct(fields))
              }
          case TransformationMode.FailFast(f) =>
            Value.Wrapped(
              ProductBinder
                .nestFlatMapsAndConstruct[F, dest](f, unwrapped.toList, wrapped.toList, construct)
            )

        }

    }
  }
}