package io.github.arainko.ducktape.internal.modules

import io.github.arainko.ducktape.*

import scala.collection.Factory
import scala.quoted.*

private[ducktape] trait LiftTransformationModule { self: Module =>
  import quotes.reflect.*

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
      case '{ Transformer.given_Transformer_Source_Option[source, dest](using $transformer) } =>
        val field = appliedTo.asExprOf[source]
        val lifted = liftTransformation(transformer, field)
        '{ Some($lifted) }.asExprOf[B]

      case '{ Transformer.given_Transformer_Option_Option[source, dest](using $transformer) } =>
        val field = appliedTo.asExprOf[Option[source]]
        '{ $field.map(src => ${ liftTransformation(transformer, 'src) }) }.asExprOf[B]

      // Seems like higher-kinded type quotes are not supported yet
      // https://github.com/lampepfl/dotty-feature-requests/issues/208
      // https://github.com/lampepfl/dotty/discussions/12446
      // Because of that we need to do some more shenanigans to get the exact collection type we transform into
      case '{
            Transformer.given_Transformer_SourceCollection_DestCollection[
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
  )(using Quotes): Option[Expr[B]] =
    TransformerLambda
      .fromTransformer(transformer)
      .map(optimizeTransformerInvocation(_, appliedTo.asTerm).asExprOf[B])

  private def optimizeTransformerInvocation(transformerLambda: TransformerLambda, appliedTo: Term): Term = {
    transformerLambda match {
      case TransformerLambda.ForProduct(param, methodCall, methodArgs) =>
        val replacer = Replacer(transformerLambda, appliedTo)
        val newArgs =
          methodArgs
            .map(replacer.transformTerm(_)(Symbol.spliceOwner)) // replace all references to the lambda param
            .map(transformTransformerInvocation) // recurse down into nested calls
        Apply(methodCall, newArgs)

      case TransformerLambda.ToAnyVal(param, constructorCall, constructorArg) =>
        Apply(constructorCall, List(appliedTo))
      case TransformerLambda.FromAnyVal(param, fieldName) =>
        Select.unique(appliedTo, fieldName)
    }

  }

  // Match on all the possibilities of method invocations (that is, named args 'field = ...' and normal (by position) terms).
  private def transformTransformerInvocation(term: Term): Term =
    term match {
      case Untyped(TransformerInvocation(transformerLambda, appliedTo)) =>
        optimizeTransformerInvocation(transformerLambda, appliedTo)
      case NamedArg(name, Untyped(TransformerInvocation(transformerLambda, appliedTo))) =>
        NamedArg(name, optimizeTransformerInvocation(transformerLambda, appliedTo))
      case other => other
    }

  /**
   * Replaces all references to 'param' of the given TransformerLamba with `appliedTo`. Eg.:
   *
   * val appliedTo = "appliedTo"
   * val name = (((param: String) => new Name(param)): Transformer.ToAnyVal[String, Name]).transform(appliedTo)
   *
   * After replacing this piece of code will look like this:
   * val name = (((param: String) => new Name(appliedTo)): Transformer.ToAnyVal[String, Name]).transform(appliedTo)
   */
  final class Replacer(transformerLambda: TransformerLambda, appliedTo: Term) extends TreeMap {
    override def transformTerm(tree: Term)(owner: Symbol): Term =
      tree match {
        // by checking symbols we know if the term refers to the TransformerLambda parameter so we can replace it
        case Select(ident: Ident, fieldName) if transformerLambda.param.symbol == ident.symbol =>
          Select.unique(appliedTo, fieldName)
        case other => super.transformTerm(other)(owner)
      }
  }

  enum TransformerLambda {
    def param: ValDef

    case ForProduct(param: ValDef, methodCall: Term, methodArgs: List[Term])
    case ToAnyVal(param: ValDef, constructorCall: Term, constructorArg: Term)
    case FromAnyVal(param: ValDef, fieldName: String)
  }

  object TransformerLambda {

    def fromTransformer(transformer: Expr[Transformer[?, ?]])(using Quotes): Option[TransformerLambda] =
      transformer match {
        case '{ $t: Transformer.ForProduct[a, b] } =>
          val stripped = StripNoisyNodes(transformer.asTerm).asExprOf[Transformer.ForProduct[a, b]]
          TransformerLambda.fromForProduct(stripped)
        case '{ $t: Transformer.FromAnyVal[a, b] } =>
          val stripped = StripNoisyNodes(transformer.asTerm).asExprOf[Transformer.FromAnyVal[a, b]]
          TransformerLambda.fromFromAnyVal(stripped)
        case '{ $t: Transformer.ToAnyVal[a, b] } =>
          val stripped = StripNoisyNodes(transformer.asTerm).asExprOf[Transformer.ToAnyVal[a, b]]
          TransformerLambda.fromToAnyVal(stripped)
        case other => None
      }

    /**
     * Matches a .make Transformer.ForProduct creation eg.:
     *
     * Transformer.ForProduct.make((p: Person) => new Person2(p.int, p.str))
     *
     * @return the parameter ('p'), a call to eg. a method ('new Person2')
     * and the args of that call ('p.int', 'p.str')
     */
    def fromForProduct(expr: Expr[Transformer.ForProduct[?, ?]])(using Quotes): Option[TransformerLambda.ForProduct] =
      PartialFunction.condOpt(expr.asTerm) {
        case MakeTransformer(param, Untyped(Apply(method, methodArgs))) =>
          TransformerLambda.ForProduct(param, method, methodArgs)
      }

    /**
     * Matches a .make Transformer.ToAnyVal creation eg.:
     *
     * final case class Name(value: String) extends AnyVal
     *
     * Transformer.ToAnyVal.make((str: String) => new Name(str))
     *
     * @return the parameter ('str'), the constructor call ('new Name') and the singular arg ('str').
     */
    def fromToAnyVal(expr: Expr[Transformer.ToAnyVal[?, ?]])(using Quotes): Option[TransformerLambda.ToAnyVal] =
      PartialFunction.condOpt(expr.asTerm) {
        case MakeTransformer(param, Untyped(Block(_, Apply(Untyped(constructorCall), List(arg))))) =>
          TransformerLambda.ToAnyVal(param, constructorCall, arg)
      }

    /**
     * Matches a .make Transformer.FromAnyVal creation eg.:
     *
     * final case class Name(value: String) extends AnyVal
     *
     * Transformer.FromAnyVal.make((name: Name) => name.value)
     *
     * @return the parameter ('name'), and the field name ('value' from the expression 'name.value')
     */
    def fromFromAnyVal(expr: Expr[Transformer.FromAnyVal[?, ?]])(using Quotes): Option[TransformerLambda.FromAnyVal] =
      PartialFunction.condOpt(expr.asTerm) {
        case MakeTransformer(param, Untyped(Block(_, Select(Untyped(_: Ident), fieldName)))) =>
          TransformerLambda.FromAnyVal(param, fieldName)
      }
  }

  object TransformerInvocation {
    def unapply(term: Term)(using Quotes): Option[(TransformerLambda, Term)] =
      term.asExpr match {
        case '{ ($transformer: Transformer.ForProduct[a, b]).transform($appliedTo) } =>
          TransformerLambda.fromForProduct(transformer).map(_ -> appliedTo.asTerm)
        case '{ ($transformer: Transformer.FromAnyVal[a, b]).transform($appliedTo) } =>
          TransformerLambda.fromFromAnyVal(transformer).map(_ -> appliedTo.asTerm)
        case '{ ($transformer: Transformer.ToAnyVal[a, b]).transform($appliedTo) } =>
          TransformerLambda.fromToAnyVal(transformer).map(_ -> appliedTo.asTerm)
        case other => None
      }
  }

  /**
   * Strips all Inlined nodes that don't introduce any new vals into the scope.
   */
  object StripNoisyNodes extends TreeMap {
    def apply(term: Term)(using Quotes): Term = transformTerm(term)(Symbol.spliceOwner)
    override def transformTerm(tree: Term)(owner: Symbol): Term =
      tree match {
        case Inlined(_, Nil, term) => transformTerm(term)(owner)
        case other                 => super.transformTerm(other)(owner)
      }
  }

  /**
   * Recursively unpacks Typed nodes
   */
  object Untyped {
    def unapply(term: Term)(using Quotes): Some[Term] =
      term match {
        case Typed(tree, _) => unapply(tree)
        case other          => Some(other)
      }
  }

  /**
   * Extracts the creator lambda param and body from a `make` expression, eg.:
   *  ForProduct.make(param => body)
   */
  object MakeTransformer {
    def unapply(term: Term)(using Quotes): Option[(ValDef, Term)] =
      PartialFunction.condOpt(term) {
        case Untyped(Apply(_, List(Untyped(Lambda(List(param), body))))) => param -> body
      }
  }
}
