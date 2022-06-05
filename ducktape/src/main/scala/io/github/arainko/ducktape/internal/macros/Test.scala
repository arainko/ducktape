package io.github.arainko.ducktape.internal.macros

import scala.quoted.*

object Test {
  inline def configs(inline confs: TypedConfig[Costam]*) = ${ configsMacro('confs) }

  def configsMacro(confs: Expr[Seq[TypedConfig[Costam]]])(using Quotes) = {
    import quotes.reflect.*

    val terms = confs match {
      case Varargs(elems) => elems
    }

    object SelectorLambda {
      def unapply(arg: Term): Option[(List[ValDef], Term)] =
        arg match {
          case Inlined(_, _, Lambda(vals, term)) => Some((vals, term))
          case Inlined(_, _, nested)             => SelectorLambda.unapply(nested)
          case _                                 => None
        }
    }

    object FieldSelector {
      def unapply(arg: Term): Option[String] =
        PartialFunction.condOpt(arg) {
          case Lambda(vals, Select(Ident(_), name)) => name
        }
    }

    Varargs.apply {
      terms.map {
        case expr @ '{ TypedConfig.const[s, a, b]($selector, $const)(using $ev) } => 
          val name = FieldSelector.unapply(selector.asTerm).get
          Expr(name)
        // case other => Expr(other.show)
      }
    }
  }
}
