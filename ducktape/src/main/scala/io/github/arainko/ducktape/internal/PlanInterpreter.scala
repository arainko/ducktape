package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.internal.*

import scala.collection.Factory
import scala.quoted.*

private[ducktape] object PlanInterpreter {

  def run[A: Type](plan: Plan[Nothing, Nothing], sourceValue: Expr[A])(using Quotes): Expr[Any] =
    recurse(plan, sourceValue)(using sourceValue)

  def recurse[A: Type](plan: Plan[Nothing, Nothing], value: Expr[Any])(using
    toplevelValue: Expr[A]
  )(using Quotes): Expr[Any] = {
    import quotes.reflect.*

    plan match {
      case Plan.Upcast(_, _, _) => value

      case Plan.Configured(_, _, config, _) =>
        evaluateConfig(config, value)

      case Plan.BetweenProducts(source, dest, fieldPlans) =>
        val args = fieldPlans.map {
          case (fieldName, plan) if source.fields.contains(fieldName) =>
            val fieldValue = value.accessFieldByName(fieldName).asExpr
            NamedArg(fieldName, recurse(plan, fieldValue).asTerm)
          case (fieldName, plan) =>
            NamedArg(fieldName, recurse(plan, value).asTerm)
        }
        Constructor(dest.tpe.repr).appliedToArgs(args.toList).asExpr

      case Plan.BetweenProductTuple(source, dest, plans) =>
        val fields = source.fields.keys
        val args = plans.zipWithIndex.map {
          case (plan, idx) if fields.isDefinedAt(idx) =>
            val fieldName = fields(idx)
            val fieldValue = value.accessFieldByName(fieldName).asExpr
            recurse(plan, fieldValue)
          case (plan, _) =>
            recurse(plan, value)
        }

        Expr.ofTupleFromSeq(args.toSeq)

      case Plan.BetweenTupleProduct(source, dest, plans) =>
        val args = plans.values.zipWithIndex.map {
          case (plan, idx) if source.elements.isDefinedAt(idx) =>
            val elemValue = value.accesFieldByIndex(idx, source)
            recurse(plan, elemValue).asTerm
          case (plan, _) =>
            recurse(plan, value).asTerm
        }
        Constructor(dest.tpe.repr).appliedToArgs(args.toList).asExpr

      case Plan.BetweenTuples(source, dest, plans) =>
        val args = plans.zipWithIndex.map {
          case (plan, idx) if source.elements.isDefinedAt(idx) =>
            val elemValue = value.accesFieldByIndex(idx, source)
            recurse(plan, elemValue)
          case (plan, _) =>
            recurse(plan, value)
        }

        Expr.ofTupleFromSeq(args)

      case Plan.BetweenCoproducts(sourceTpe, destTpe, casePlans) =>
        val branches = casePlans.map { plan =>
          (plan.source.tpe -> plan.dest.tpe) match {
            case '[src] -> '[dest] =>
              val sourceValue = '{ $value.asInstanceOf[src] }
              IfExpression.Branch(IsInstanceOf(value, plan.source.tpe), recurse(plan, sourceValue))
          }
        }.toList
        IfExpression(branches, '{ throw new RuntimeException("Unhandled case. This is most likely a bug in ducktape.") }).asExpr

      case Plan.BetweenProductFunction(source, dest, argPlans) =>
        val args = argPlans.map {
          case (fieldName, plan) if source.fields.contains(fieldName) =>
            val fieldValue = value.accessFieldByName(fieldName).asExpr
            recurse(plan, fieldValue).asTerm
          case (fieldName, plan) =>
            recurse(plan, value).asTerm
        }
        dest.function.appliedTo(args.toList)

      case Plan.BetweenTupleFunction(source, dest, argPlans) =>
        val args = argPlans.values.zipWithIndex.map {
          case (plan, index) if source.elements.isDefinedAt(index) =>
            val fieldValue = value.accesFieldByIndex(index, source)
            recurse(plan, fieldValue).asTerm
          case (plan, index) =>
            recurse(plan, value).asTerm
        }
        dest.function.appliedTo(args.toList)

      case Plan.BetweenOptions(sourceTpe, destTpe, plan) =>
        (sourceTpe.paramStruct.tpe -> destTpe.paramStruct.tpe) match {
          case '[src] -> '[dest] =>
            val optionValue = value.asExprOf[Option[src]]
            def transformation(value: Expr[src])(using Quotes): Expr[dest] = recurse(plan, value).asExprOf[dest]
            '{ $optionValue.map(src => ${ transformation('src) }) }
        }

      case Plan.BetweenNonOptionOption(sourceTpe, destTpe, plan) =>
        (sourceTpe.tpe -> destTpe.paramStruct.tpe) match {
          case '[src] -> '[dest] =>
            val sourceValue = value.asExprOf[src]
            def transformation(value: Expr[src])(using Quotes): Expr[dest] = recurse(plan, value).asExprOf[dest]
            '{ Some(${ transformation(sourceValue) }) }
        }

      case Plan.BetweenCollections(source, dest, collFactory, plan) =>
        (dest.tpe, source.paramStruct.tpe, dest.paramStruct.tpe) match {
          case ('[destCollTpe], '[srcElem], '[destElem]) =>
            val sourceValue = value.asExprOf[Iterable[srcElem]]
            val factory = collFactory.asExprOf[Factory[destElem, destCollTpe]]
            def transformation(value: Expr[srcElem])(using Quotes): Expr[destElem] = recurse(plan, value).asExprOf[destElem]
            '{ $sourceValue.map(src => ${ transformation('src) }).to($factory) }
        }

      case Plan.BetweenSingletons(sourceTpe, destTpe) => destTpe.value

      case Plan.BetweenWrappedUnwrapped(sourceTpe, destTpe, fieldName) =>
        value.accessFieldByName(fieldName).asExpr

      case Plan.BetweenUnwrappedWrapped(sourceTpe, destTpe) =>
        Constructor(destTpe.tpe.repr).appliedTo(value.asTerm).asExpr

      case Plan.UserDefined(source, dest, transformer) =>
        transformer match {
          case Summoner.UserDefined.TotalTransformer(transformer) =>
            transformer match {
              case '{ $t: Transformer[src, dest] } =>
                val sourceValue = value.asExprOf[src]
                '{ $t.transform($sourceValue) }
            }
        }

      case Plan.Derived(source, dest, transformer) =>
        transformer match {
          case Summoner.Derived.TotalTransformer(transformer) =>
            transformer match {
              case '{ $t: Transformer.Derived[src, dest] } =>
                val sourceValue = value.asExprOf[src]
                '{ $t.transform($sourceValue) }
            }
        }
    }
  }

  def evaluateConfig[A: Type](config: Configuration[Nothing], value: Expr[Any])(using toplevelValue: Expr[A], quotes: Quotes) =
    config match {
      case Configuration.Const(value, _) =>
        value
      case Configuration.CaseComputed(_, function) =>
        '{ $function.apply($value) }
      case Configuration.FieldComputed(_, function) =>
        '{ $function.apply($toplevelValue) }
      case Configuration.FieldComputedDeep(tpe, sourceTpe, function) =>
        /*Expr.betaReduce(*/'{ $function.apply($value) }/*)*/
      case Configuration.FieldReplacement(source, name, tpe) =>
        source.accessFieldByName(name).asExpr
    }
}
