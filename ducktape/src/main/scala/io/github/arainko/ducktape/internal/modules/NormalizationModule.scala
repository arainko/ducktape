package io.github.arainko.ducktape.internal.modules

import io.github.arainko.ducktape.*

import scala.quoted.*

private[ducktape] trait NormalizationModule { self: Module =>
  import quotes.reflect.*

  def normalizeTransformer[A: Type, B: Type](transformer: Expr[Transformer[A, B]], appliedTo: Expr[A])(using Quotes) = {
    val stripped = StripNoisyNodes.transformTerm(transformer.asTerm)(Symbol.spliceOwner).asExprOf[Transformer[A, B]]
    val transformerLambda =
      stripped match {
        case '{ ($transformer: Transformer.ForProduct[a, b]) } =>
          TransformerLambda.fromForProduct(transformer)
        case '{ ($transformer: Transformer.FromAnyVal[a, b]) } =>
          TransformerLambda.fromFromAnyVal(transformer)
        case '{ ($transformer: Transformer.ToAnyVal[a, b]) } =>
          TransformerLambda.fromToAnyVal(transformer)
      }
    transformerLambda
      .map(optimizeTransformerInvocation(_, appliedTo.asTerm))
      .map(_.asExprOf[B])
      .getOrElse('{ $transformer.transform($appliedTo) })
  }

  // Match on all the possibilieties of method invocations (that is, named args 'field = ...' and normalterms).
  private def transformTransformerInvocation(term: Term): Term =
    term match {
      case Untyped(TransformerInvocation(transformerLambda, appliedTo)) =>
        optimizeTransformerInvocation(transformerLambda, appliedTo)
      case NamedArg(name, Untyped(TransformerInvocation(transformerLambda, appliedTo))) =>
        NamedArg(name, optimizeTransformerInvocation(transformerLambda, appliedTo))
      case other => other
    }

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

  /**
   * Replaces all instances of 'param' of the given TransformerLamba with `appliedTo`. Eg.:
   *
   * val name = (((param: String) => new Name(param)): Transformer.ToAnyVal[String, Name]).transform("appliedTo")
   *
   * After replacing this piece of code will look like this:
   * val name = new Name("appliedTo")
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

    /**
     * Matches a .make Transformer.ForProduct creation eg.:
     *
     * Transformer.ForProduct.make((p: Person) => new Person2(p.int, p.str))
     *
     * @return the parameter ('p'), a call to eg. a method ('new Person2')
     * and the args of that call ('p.int', 'p.str')
     */
    def fromForProduct(expr: Expr[Transformer.ForProduct[?, ?]]): Option[TransformerLambda.ForProduct] =
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
    def fromToAnyVal(expr: Expr[Transformer.ToAnyVal[?, ?]]): Option[TransformerLambda.ToAnyVal] =
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
    def fromFromAnyVal(expr: Expr[Transformer.FromAnyVal[?, ?]]): Option[TransformerLambda.FromAnyVal] =
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
