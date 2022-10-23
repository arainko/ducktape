package io.github.arainko.ducktape.internal.modules

import io.github.arainko.ducktape.*

import scala.quoted.*
import scala.reflect.TypeTest

trait NormalizationModule { self: Module =>
  import quotes.reflect.*

  def normalize[A: Type](expr: Expr[A]): Expr[A] = {
    // println(StripInlinedAndTyped.transformTree(expr.asTerm)(Symbol.spliceOwner).show)

    val stripped = StripNoisyNodes.transformTerm(expr.asTerm)(Symbol.spliceOwner)
    println(stripped.show(using Printer.TreeStructure))
    val normalized = normalizeTerm(stripped)
    // println(normalized.show)
    normalized.asExprOf[A]
  }

  private def normalizeTerm(term: Term): Term =
    term match {
      case Inlined(call, stats, tree) => Inlined.copy(term)(call, stats, normalizeTerm(tree))
      case app @ Apply(call, args) =>
        Apply.copy(app)(call, args.map(transformToplevelArg))
      case other => other
    }

  // object Uninlined {
  //   def unapply(term: Term): Term = term match {
  //     case Inlined(call, stats, tree) =>
  //   }
  // }

  // Match on all the possibilieties of method invocations (that is, named args 'field = ...' and normal param passing).
  private def transformToplevelArg(term: Term): Term =
    term match {
      // case Inlined(call, stats, term) => Inlined.copy(term)(call, stats, transformToplevelArg(term))
      case NamedArg(name, TransformerInvocation(transformerLambda, appliedTo)) =>
        NamedArg(name, optimizeTransformerInvocation(transformerLambda, appliedTo))
      case TransformerInvocation(transformerLambda, appliedTo) =>
        optimizeTransformerInvocation(transformerLambda, appliedTo)
      case other => other
    }

  private def optimizeTransformerInvocation(transformerLambda: TransformerLambda, appliedTo: Term): Term = {
    transformerLambda match {
      case TransformerLambda.ForProduct(param, methodCall, methodArgs) =>
        val replacer = Replacer(transformerLambda, appliedTo)
        val newArgs =
          methodArgs
            .map(replacer.transformTerm(_)(Symbol.spliceOwner)) // replace all references to the lambda param
            .map(transformToplevelArg) // recurse down into nested calls
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
        case Typed(term, _) => transformTerm(term)(owner)
        case other          => super.transformTerm(other)(owner)
      }
  }

  enum TransformerLambda {
    def param: ValDef

    case ForProduct(param: ValDef, methodCall: Term, methodArgs: List[Term])
    case ToAnyVal(param: ValDef, constructorCall: New, constructorArg: Term)
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
        case Lambda(List(param), Apply(method, methodArgs)) =>
          TransformerLambda.ForProduct(param, method, methodArgs)
      }

    def fromToAnyVal(expr: Expr[Transformer.ToAnyVal[?, ?]]): Option[TransformerLambda.ToAnyVal] =
      PartialFunction.condOpt(expr.asTerm) {
        case Lambda(List(param), Apply(constructorCall: New, List(arg))) =>
          TransformerLambda.ToAnyVal(param, constructorCall, arg)
      }

    def fromFromAnyVal(expr: Expr[Transformer.FromAnyVal[?, ?]]): Option[TransformerLambda.FromAnyVal] =
      PartialFunction.condOpt(expr.asTerm) {
        case Lambda(List(param), Select(_: Ident, fieldName)) =>
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
}
