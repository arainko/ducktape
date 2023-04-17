package io.github.arainko.ducktape.internal.macros

import io.github.arainko.ducktape.Transformer
import io.github.arainko.ducktape.internal.modules.TransformerLambda.*
import io.github.arainko.ducktape.internal.modules.*

import scala.collection.Factory
import scala.quoted.*

//TODO: if this is moved to `modules` the compiler crashes, investigate further?
private[ducktape] object LiftTransformation {

  def liftTransformation[A: Type, B: Type](transformer: Expr[Transformer[A, B]], appliedTo: Expr[A])(using Quotes): Expr[B] =
    liftIdentityTransformation(transformer, appliedTo)
      .orElse(liftBasicTransformation(transformer, appliedTo))
      .orElse(liftDerivedTransformation(transformer, appliedTo))
      .getOrElse('{ $transformer.transform($appliedTo) })

  /**
   * Rewrites `Transformer.Identity[A, B].transform(valueOfA)` to just `valueOfA` since we know that Identity transformers
   * only exist when B is a supertype of A
   */
  private def liftIdentityTransformation[A: Type, B: Type](
    transformer: Expr[Transformer[A, B]],
    appliedTo: Expr[A]
  )(using Quotes): Option[Expr[B]] =
    PartialFunction.condOpt(transformer) {
      case '{ $identityTransformer: Transformer.Identity[?, ?] } => appliedTo.asExprOf[B]
    }

  /**
   * Lifts transformations from 'basic' Transformers (eg. A => Option[A], Option[A] => Option[B], Collection1[A] => Collection2[B])
   */
  private def liftBasicTransformation[A: Type, B: Type](
    transformer: Expr[Transformer[A, B]],
    appliedTo: Expr[A]
  )(using Quotes): Option[Expr[B]] =
    PartialFunction.condOpt(transformer) {
      case '{ Transformer.betweenNonOptionOption[source, dest](using $transformer) } =>
        val field = appliedTo.asExprOf[source]
        val lifted = liftTransformation(transformer, field)
        '{ Some($lifted) }.asExprOf[B]

      case '{ Transformer.betweenOptions[source, dest](using $transformer) } =>
        val field = appliedTo.asExprOf[Option[source]]
        '{ $field.map(src => ${ liftTransformation(transformer, 'src) }) }.asExprOf[B]

      // Seems like higher-kinded type quotes are not supported yet
      // https://github.com/lampepfl/dotty-feature-requests/issues/208
      // https://github.com/lampepfl/dotty/discussions/12446
      // Because of that we need to do some more shenanigans to get the exact collection type we transform into
      case '{
            Transformer.betweenCollections[
              source,
              dest,
              Iterable,
              Iterable
            ](using $transformer, $factory)
          } =>
        val field = appliedTo.asExprOf[Iterable[source]]
        factory match {
          case '{ $f: Factory[`dest`, destColl] } =>
            '{ $field.map(src => ${ liftTransformation(transformer, 'src) }).to($f) }.asExprOf[B]
        }
    }

  /**
   * Lifts a raw transformation from a derived Transformer, eg.:
   * {{{
   * final case class Person(age: Int, name: String)
   *
   * final case class Person2(age: Int, name: String)
   *
   * val person = Person(23, "PersonName")
   *
   * inline def transformed: Person2 =
   *   Transformer.ForProduct.make((p: Person) => new Person2(age = p.age, name = p.name)).transform(person)
   *
   * val lifted: Person2 = liftTransformation(transformed)
   * // the AST of 'lifted' is now 'new Person2(age = person.age, name = person.name)'
   * }}}
   */
  private def liftDerivedTransformation[A: Type, B: Type](
    transformer: Expr[Transformer[A, B]],
    appliedTo: Expr[A]
  )(using Quotes): Option[Expr[B]] = {
    TransformerLambda
      .fromTransformer(transformer)
      .map(optimizeTransformerInvocation(_, appliedTo).asExprOf[B])
  }

  private def optimizeTransformerInvocation(using Quotes)(
    transformerLambda: TransformerLambda[quotes.type],
    appliedTo: Expr[Any]
  ): quotes.reflect.Term = {
    import quotes.reflect.*

    transformerLambda match {
      case tl: TransformerLambda.ForProduct[quotes.type] =>
        val replacer = Replacer(transformerLambda, appliedTo)
        val newArgs =
          tl.methodArgs
            .map(replacer.apply) // replace all references to the lambda param
            .map(transformTransformerInvocation) // recurse down into nested calls

        tl.defs match {
          case Nil =>
            Apply(tl.methodCall, newArgs)
          case nonEmpty =>
            Inlined(None, nonEmpty, Apply(tl.methodCall, newArgs))
        }

      case tl: TransformerLambda.ToAnyVal[quotes.type] =>
        Apply(tl.constructorCall, List(appliedTo.asTerm))

      case tl: TransformerLambda.FromAnyVal[quotes.type] =>
        Select.unique(appliedTo.asTerm, tl.fieldName)
    }
  }

  // Match on all the possibilities of method invocations (that is, named args 'field = ...' and normal (by position) terms).
  private def transformTransformerInvocation(using Quotes)(term: quotes.reflect.Term): quotes.reflect.Term = {
    import quotes.reflect.*

    term match {
      case Untyped(TransformerInvocation(transformerLambda, appliedTo)) =>
        optimizeTransformerInvocation(transformerLambda, appliedTo.asExpr)
      case NamedArg(name, Untyped(TransformerInvocation(transformerLambda, appliedTo))) =>
        NamedArg(name, optimizeTransformerInvocation(transformerLambda, appliedTo.asExpr))
      case other => other
    }
  }
}
