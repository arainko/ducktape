package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.internal.Summoner.UserDefined.{ FallibleTransformer, TotalTransformer }
import io.github.arainko.ducktape.{ Mode, Transformer }

import scala.collection.Factory
import scala.collection.immutable.VectorMap
import scala.quoted.*

private[ducktape] object FalliblePlanInterpreter {
  def run[F[+x]: Type, A: Type, B: Type](
    plan: Plan[Nothing, Fallible],
    sourceValue: Expr[A],
    mode: TransformationMode[F]
  )(using Quotes): Expr[F[B]] =
    recurse(plan, sourceValue, mode)(using sourceValue).wrapped(mode, Type.of[B])

  private def recurse[F[+x]: Type, A: Type](
    plan: Plan[Nothing, Fallible],
    value: Expr[Any],
    F: TransformationMode[F]
  )(using toplevelValue: Expr[A])(using Quotes): Value[F] = {
    import quotes.reflect.*

    FallibilityRefiner.run(plan) match
      case plan: Plan[Nothing, Nothing] =>
        // TODO: Running with '-Xcheck-macros' exposes an issue with owners of lambdas (like in BetweenOptions), try to fix it later on
        Value.Unwrapped(PlanInterpreter.recurse[A](plan, value))
      case None =>
        plan match {
          case Plan.Upcast(_, _) => Value.Unwrapped(value)

          case Plan.Configured(_, _, config, _) =>
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
            fromProductTransformation(plan, source, fieldPlans, value, F)(ProductConstructor.Primary(dest))

          case plan @ Plan.BetweenProductTuple(source, dest, plans) =>
            fromProductTransformation(plan, source, plans, value, F)(ProductConstructor.Tuple)

          case plan @ Plan.BetweenTupleProduct(source, dest, plans) =>
            fromTupleTransformation(source, plan, plans.values.toVector, value, F)(ProductConstructor.Primary(dest))

          case plan @ Plan.BetweenTuples(source, dest, plans) =>
            fromTupleTransformation(source, plan, plans, value, F)(ProductConstructor.Tuple)

          case plan @ Plan.BetweenFallibleNonFallible(source, dest, elemPlan) =>
            (source.underlying.tpe, dest.tpe) match {
              case '[src] -> '[dest] =>
                val src = value.asExprOf[F[src]]
                Value.Wrapped('{ ${ F.value }.map($src, a => ${ PlanInterpreter.recurse(elemPlan, 'a).asExprOf[dest] }) })
            }

          case plan @ Plan.BetweenFallibles(source, dest, mode, elemPlan) =>
            FallibilityRefiner.run(elemPlan) match {
              case plan: Plan[Nothing, Nothing] =>
                val simplified = Plan.BetweenFallibleNonFallible(source, dest, plan)
                recurse(simplified, value, F)
              case None =>
                (source.underlying.tpe, dest.tpe) match {
                  case '[src] -> '[dest] =>
                    val localMode = mode.value.asExprOf[Mode.FailFast[F]]
                    val src = value.asExprOf[F[src]]

                    Value.Wrapped('{ $localMode.flatMap($src, a => ${ recurse(elemPlan, 'a, F).wrapped(F, Type.of[dest]) }) })
                }
            }

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
            fromProductTransformation(plan, source, argPlans, value, F)(ProductConstructor.Func(dest.function))

          case plan @ Plan.BetweenTupleFunction(source, dest, argPlans) =>
            fromTupleTransformation(source, plan, argPlans.values.toVector, value, F)(ProductConstructor.Func(dest.function))

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
    final def wrapped[A](F: TransformationMode[F], tpe: Type[A])(using Quotes, Type[F]): Expr[F[A]] = {
      given Type[A] = tpe

      this match
        case Unwrapped(value) =>
          '{ ${ F.value }.pure[A](${ value.asExprOf[A] }) }
        case Wrapped(value) =>
          value.asExprOf[F[A]]
    }

    final def asFieldValue(index: Int, tpe: Type[?]): Either[FieldValue.Unwrapped, FieldValue.Wrapped[F]] =
      this match {
        case unw: Unwrapped[F]   => Left(new FieldValue.Unwrapped(index, tpe, unw.value))
        case wrapped: Wrapped[F] => Right(new FieldValue.Wrapped(index, tpe, wrapped.value))
      }

    case Unwrapped(value: Expr[Any])
    case Wrapped(value: Expr[F[Any]])
  }

  private def fromTupleTransformation[F[+x]: Type, A: Type](
    sourceStruct: Structure.Tuple,
    plan: Plan[Nothing, Fallible],
    plans: Vector[Plan[Nothing, Fallible]],
    value: Expr[Any],
    F: TransformationMode[F]
  )(construct: ProductConstructor)(using quotes: Quotes, toplevelValue: Expr[A]) = {
    import quotes.reflect.*

    val (unwrapped, wrapped) =
      plans.zipWithIndex.partitionMap {
        case (p: Plan.Configured[Fallible]) -> index =>
          recurse(p, value, F).asFieldValue(index, p.dest.tpe)
        case plan -> index =>
          val fieldValue = value.accesFieldByIndex(index, sourceStruct)
          recurse(plan, fieldValue, F).asFieldValue(index, plan.dest.tpe)
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
                val fields = unwrapped.map(field => field.value).toVector
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

  // TODO: this is pretty hideous, please refactor this later on
  private def fromProductTransformation[F[+x]: Type, A: Type](
    plan: Plan[Nothing, Fallible],
    source: Structure.Product,
    fieldPlans: Vector[Plan[Nothing, Fallible]] | VectorMap[String, Plan[Nothing, Fallible]],
    value: Expr[Any],
    F: TransformationMode[F]
  )(construct: ProductConstructor)(using quotes: Quotes, toplevelValue: Expr[A]) = {
    import quotes.reflect.*

    def handleVectorMap(fieldPlans: VectorMap[String, Plan[Nothing, Fallible]])(using Quotes) =
      fieldPlans.zipWithIndex.partitionMap {
        case (fieldName, p: Plan.Configured[Fallible]) -> index =>
          recurse(p, value, F).asFieldValue(index, p.dest.tpe)
        case (fieldName, plan) -> index =>
          val fieldValue = value.accessFieldByName(fieldName).asExpr
          recurse(plan, fieldValue, F).asFieldValue(index, plan.dest.tpe)
      }

    def handleVector(fieldPlans: Vector[Plan[Nothing, Fallible]])(using Quotes) = {
      val sourceFields = source.fields.keys
      fieldPlans.zipWithIndex.partitionMap {
        case (p: Plan.Configured[Fallible]) -> index =>
          recurse(p, value, F).asFieldValue(index, p.dest.tpe)
        case plan -> index =>
          val fieldName = sourceFields(index)
          val fieldValue = value.accessFieldByName(fieldName).asExpr
          recurse(plan, fieldValue, F).asFieldValue(index, plan.dest.tpe)
      }
    }

    val (unwrapped, wrapped) =
      fieldPlans match
        case vector: Vector[Plan[Nothing, Fallible]]               => handleVector(vector)
        case vectorMap: VectorMap[String, Plan[Nothing, Fallible]] => handleVectorMap(vectorMap)

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
                val fields = unwrapped.map(field => field.value).toVector
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
