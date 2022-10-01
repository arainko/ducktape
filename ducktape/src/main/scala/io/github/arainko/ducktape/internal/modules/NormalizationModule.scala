package io.github.arainko.ducktape.internal.modules

import scala.quoted.*
import io.github.arainko.ducktape.*

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
    
    val res = stripInlinedAndTyped.transformTree(expr.asTerm)(Symbol.spliceOwner) match {
      case Apply(call, args) =>
        val refinedArgs = args.map {
          case NamedArg(name, TransformerInvocation(TransformerLambda(argName, call, argss), appliedTo)) => 
            // report.info(appliedTo.show)
            // report.info(argss.map(_.show).mkString(", "))
            val newArgs = argss.map {
              case NamedArg(name, Select(Ident(`argName`), fieldName)) =>
                // report.info(appliedTo.show)
                NamedArg(name, Select.unique(appliedTo, fieldName))
              case other => other
            }

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
    // report.info(res.show)
    res.asExprOf[A]
    // stripBullshit.transformTree(expr.asTerm)(Symbol.spliceOwner).asExprOf[A]
  }

  object stripInlinedAndTyped extends TreeMap {
    override def transformTerm(tree: Term)(owner: Symbol): Term = 
      tree match {
        case Inlined(_, _, term) => transformTerm(term)(owner)
        case Typed(term, _) => transformTerm(term)(owner)
        case other => super.transformTerm(other)(owner)
      }
  }

  // def extractTransformerMacro[A: Type](value: Expr[A]) = {

  //   val trans -> appliedTo = value match {
  //     case '{ ($transformer: Transformer[a, b]).transform($that) } =>
  //       transformer -> that
  //     case other => report.errorAndAbort(other.asTerm.show)
  //   }

  //   val expr = trans.asTerm match {
  //     case Untyped(TransformerLambda(arg, call, args)) =>
  //       // report.info(term.show)
  //       val fieldNameds = args.map {
  //         case Select(Ident(`arg`), fieldName) => Select.unique(appliedTo.asTerm, fieldName)
  //         case other                           => other
  //       }
  //       Apply(call, fieldNameds)

  //     case tree =>
  //       report.errorAndAbort(tree.show)
  //   }

  //   expr.asExprOf[A]
  // }

  /**
   * Matches a SAM Transformer creation eg.:
   *
   * ((p: Person) => new Person2(p.int, p.str)): Transformer[Person, Person2]
   *
   * @return a name of the parameter ('p'), a call to eg. a method ('new Person2')
   * and the args of that call ('p.int', 'p.str')
   */
  object TransformerLambda {
    def unapply(term: Term): Option[(String, Term, List[Term])] =
      PartialFunction.condOpt(term) {
        case Lambda(List(ValDef(arg, _, _)), Apply(call, args)) => (arg, call, args)
      }
  }

  object TransformerInvocation {
    def unapply(term: Term)(using Quotes): Option[(Term, Term)] =
      PartialFunction.condOpt(term.asExpr) {
        case '{ ($transformer: Transformer.ForProduct[a, b]).transform($appliedTo) } =>
          transformer.asTerm -> appliedTo.asTerm
      }
  }
}

