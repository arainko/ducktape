package io.github.arainko.ducktape.internal.modules

import io.github.arainko.ducktape.*
import io.github.arainko.ducktape.derived.*

import scala.quoted.*
import scala.reflect.TypeTest


trait NormalizationModule { self: Module =>
  import quotes.reflect.*

  def normalize[A: Type](expr: Expr[A]): Expr[A] = {
    val normalized =
      StripInlinedAndTyped.transformTree(expr.asTerm)(Symbol.spliceOwner) match {
        case Apply(call, args) => Apply(call, args.map(transformArg))
        case other             => other
      }
    normalized.asExprOf[A]
  }

  private def transformArg(term: Term): Term =
    term match {
      case NamedArg(name, TransformerInvocation(transformerLambda, appliedTo)) =>
        val replacer = Replacer(transformerLambda, appliedTo)
        val newArgs =
          transformerLambda.methodArgs
            .map(replacer.transformTerm(_)(Symbol.spliceOwner)) // replace all references to the lambda param
            .map(transformArg) // recurse down into nested calls

        NamedArg(name, Apply(transformerLambda.methodCall, newArgs))
      case other => other
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

  object StripInlinedAndTyped extends TreeMap {
    override def transformTerm(tree: Term)(owner: Symbol): Term =
      tree match {
        case Inlined(_, _, term) => transformTerm(term)(owner)
        case Typed(term, _)      => transformTerm(term)(owner)
        case other               => super.transformTerm(other)(owner)
      }
  }

  final class TransformerLambda(val param: ValDef, val methodCall: Term, val methodArgs: List[Term])

  /**
   * Matches a SAM Transformer creation eg.:
   *
   * ((p: Person) => new Person2(p.int, p.str)): Transformer[Person, Person2]
   *
   * @return the parameter ('p'), a call to eg. a method ('new Person2')
   * and the args of that call ('p.int', 'p.str')
   */
  object TransformerLambda {
    def unapply(term: Term): Option[TransformerLambda] =
      PartialFunction.condOpt(term) {
        case Lambda(List(param), Apply(method, methodArgs)) =>
          TransformerLambda(param, method, methodArgs)
      }
  }

  object TransformerInvocation {
    def unapply(term: Term): Option[(TransformerLambda, Term)] =
      PartialFunction.condOpt(term.asExpr) {
        case '{ ForProduct.make[a, b]($lambda).transform($appliedTo) } =>
          report.info(lambda.asTerm.show)
          TransformerLambda.unapply(lambda.asTerm).get -> appliedTo.asTerm //TODO: delete the .get call
        case other => 
          println(other.asTerm.show)
          
      }
  }
}
