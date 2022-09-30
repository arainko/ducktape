package io.github.arainko.ducktape.internal.modules

import scala.quoted.*
import io.github.arainko.ducktape.*

trait NormalizationModule extends Module {
  import quotes.reflect.*

  def extractTransformerMacro[A: Type](value: Expr[A]) = {

    val trans -> appliedTo = value match {
      case '{ ($transformer: Transformer[a, b]).transform($that) } =>
        transformer -> that
      case other => report.errorAndAbort(other.asTerm.show)
    }

    val expr = trans.asTerm match {
      case Untyped(TransformerLambda(arg, call, args)) =>
        // report.info(term.show)
        val fieldNameds = args.map {
          case Select(Ident(`arg`), fieldName) => Select.unique(appliedTo.asTerm, fieldName)
          case other                           => other
        }
        Apply(call, fieldNameds)

      case tree =>
        report.errorAndAbort(tree.show)
    }

    expr.asExprOf[A]
  }

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

  /**
    * Strips all `Typed` AST nodes
    */
  object Untyped {
    def unapply(term: Term): Some[Term] =
      term match {
        case Typed(tree, _) => unapply(tree)
        case other          => Some(other)
      }
  }
}
