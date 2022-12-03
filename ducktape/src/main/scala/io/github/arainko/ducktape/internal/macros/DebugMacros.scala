package io.github.arainko.ducktape.internal.macros

import io.github.arainko.ducktape.*

import scala.quoted.*

object DebugMacros {
  inline def structure[A](inline value: A) = ${ structureMacro('value) }

  def structureMacro[A: Type](value: Expr[A])(using Quotes) = {
    import quotes.reflect.*

    val struct = Printer.TreeStructure.show(value.asTerm)
    report.info(struct)
    value
  }

  inline def code[A](inline value: A) = ${ codeCompiletimeMacro('value) }

  def codeCompiletimeMacro[A: Type](value: Expr[A])(using Quotes) = {
    import quotes.reflect.*
    val struct = Printer.TreeShortCode.show(value.asTerm)
    report.info(struct)
    value
  }

  inline def matchTest[A, B](inline transformer: Transformer.ForProduct[A, B]) = ${ matchTestMacro('transformer) }

  def matchTestMacro[A: Type, B: Type](transformer: Expr[Transformer.ForProduct[A, B]])(using Quotes) = {
    import quotes.reflect.*

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

    val stripped = StripNoisyNodes.transformTerm(transformer.asTerm)(Symbol.spliceOwner)

    stripped match {
      case MakeTransformer(param, body) => report.info("GOT IT")
      case other => report.info(other.show(using Printer.TreeStructure))
    }

    println(stripped.show(using Printer.TreeStructure))
    '{}
  }
}
