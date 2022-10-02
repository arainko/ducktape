package io.github.arainko.ducktape.internal.modules

import scala.quoted.*
import io.github.arainko.ducktape.*
import scala.reflect.TypeTest

/* 
  zaczynam od: 
    new Person(int = from.int, str = from.str, inside = (...).transform(from.inside))
  czyli Apply(call = new Person, args = List(from.int, from.str, ...))
  rekursywnie upraszczam AST gdzie wolane jest Transformer.ForProduct

*/

trait NormalizationModule { self: Module =>
  import quotes.reflect.*

  def normalize[A: Type](expr: Expr[A]): Expr[A] = {
    given Printer[Tree] = Printer.TreeShortCode
    
    val res = StripInlinedAndTyped.transformTree(expr.asTerm)(Symbol.spliceOwner) match {
      case Apply(call, args) =>
        val refinedArgs = args.map {
          case NamedArg(name, TransformerInvocation(TransformerLambda(vd, call, argss), appliedTo)) => 
            object replacer extends TreeMap {
              override def transformTerm(tree: Term)(owner: Symbol): Term = 
                tree match {
                  // by checking symbols we know if the term refers to the TransformerLambda parameter so we can replace it
                  case Select(ident: Ident, fieldName) if vd.symbol == ident.symbol =>
                    Select.unique(appliedTo, fieldName)
                  case other => super.transformTerm(other)(owner)
                }
            }


            val newArgs = argss.map(replacer.transformTerm(_)(Symbol.spliceOwner))

            // report.info(s"t1: ${call.show}, t2: ${appliedTo.show}")
            NamedArg(name, Apply(call, newArgs))
          case other => other
        }
        // report.info(args.map(_.show).mkString(", "))
        Apply(call, refinedArgs)
      case other =>
        // report.info(s"still other ${other.show}")
        other
    }
    report.info(res.show)
    res.asExprOf[A]
    // stripInlinedAndTyped.transformTree(expr.asTerm)(Symbol.spliceOwner).asExprOf[A]
  }

  object StripInlinedAndTyped extends TreeMap {
    override def transformTerm(tree: Term)(owner: Symbol): Term = 
      tree match {
        case Inlined(_, _, term) => transformTerm(term)(owner)
        case Typed(term, _) => transformTerm(term)(owner)
        case other => super.transformTerm(other)(owner)
      }
  }

  class Replacer extends TreeMap {

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
    def unapply(term: Term): Option[(Term, Term)] =
      PartialFunction.condOpt(term.asExpr) {
        case '{ ($transformer: Transformer.ForProduct[a, b]).transform($appliedTo) } =>
          transformer.asTerm -> appliedTo.asTerm
      }
  }
}

