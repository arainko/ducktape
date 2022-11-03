package io.github.arainko.ducktape.internal.modules

import io.github.arainko.ducktape.*

import scala.quoted.*
import scala.reflect.TypeTest

trait NormalizationModule { self: Module =>
  import quotes.reflect.*

  def normalize[A: Type](expr: Expr[A]): Expr[A] = {
    val stripped = StripNoisyNodes.transformTerm(expr.asTerm)(Symbol.spliceOwner)
    val normalized = normalizeTerm(stripped)
    normalized.asExprOf[A]
  }

  private def normalizeTerm(term: Term): Term =
    term match {
      case Inlined(call, stats, tree) => Inlined.copy(term)(call, stats.map(normalizeStatement), normalizeTerm(tree))
      case Typed(tree, tt)            => Typed.copy(term)(normalizeTerm(tree), tt)
      case Block(stats, tree)         => Block.copy(term)(stats.map(normalizeStatement), normalizeTerm(tree))
      case Apply(call, args)          => Apply.copy(term)(call, args.map(transformTransformerInvocation))
      case other                      => other
    }

  private def normalizeStatement[A >: ValDef <: Tree](statement: A) =
    statement match {
      case vd @ ValDef(name, tt, term) =>
        ValDef.copy(statement)(name, tt, term.map(transformTransformerInvocation(_).changeOwner(vd.symbol)))
      case other => other
    }

  // Match on all the possibilieties of method invocations (that is, named args 'field = ...' and normal param passing).
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

  class Replacer(transformerLambda: TransformerLambda, appliedTo: Term) extends TreeMap {
    override def transformTerm(tree: Term)(owner: Symbol): Term =
      tree match {
        // by checking symbols we know if the term refers to the TransformerLambda parameter so we can replace it
        case Select(ident: Ident, fieldName) if transformerLambda.param.symbol == ident.symbol =>
          Select.unique(appliedTo, fieldName)
        case other => super.transformTerm(other)(owner)
      }
  }

  object StripNoisyNodes extends TreeMap {
    override def transformTerm(tree: Term)(owner: Symbol): Term =
      tree match {
        case Inlined(_, Nil, term) => transformTerm(term)(owner)
        case other                 => super.transformTerm(other)(owner)
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
     * Matches a SAM Transformer creation eg.:
     *
     * ((p: Person) => new Person2(p.int, p.str)): Transformer[Person, Person2]
     *
     * @return the parameter ('p'), a call to eg. a method ('new Person2')
     * and the args of that call ('p.int', 'p.str')
     */
    def fromForProduct(expr: Expr[Transformer.ForProduct[?, ?]]): Option[TransformerLambda.ForProduct] = 
      PartialFunction.condOpt(expr.asTerm) {
        case MakeTransformer(param, Untyped(Apply(method, methodArgs))) =>
          TransformerLambda.ForProduct(param, method, methodArgs)
      }
    

    def fromToAnyVal(expr: Expr[Transformer.ToAnyVal[?, ?]]): Option[TransformerLambda.ToAnyVal] =
      PartialFunction.condOpt(expr.asTerm) {
        case MakeTransformer(param, Untyped(Apply(Untyped(constructorCall), List(arg)))) =>
          TransformerLambda.ToAnyVal(param, constructorCall, arg)
      }

    def fromFromAnyVal(expr: Expr[Transformer.FromAnyVal[?, ?]]): Option[TransformerLambda.FromAnyVal] =
      PartialFunction.condOpt(expr.asTerm) {
        case MakeTransformer(param, Untyped(Select(Untyped(_: Ident), fieldName))) =>
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
        case Untyped(Apply(_, List(Lambda(List(param), body)))) => param -> body
      }
  }
}
