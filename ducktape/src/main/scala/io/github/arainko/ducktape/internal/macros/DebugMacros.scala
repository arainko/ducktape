package io.github.arainko.ducktape.internal.macros

import io.github.arainko.ducktape.*

import scala.quoted.*

object DebugMacros {
  inline def structure[A](inline value: A) = ${ structureMacro('value) }

  def structureMacro[A: Type](value: Expr[A])(using Quotes) = {
    import quotes.reflect.*

    object StripInlinedAndTyped extends TreeMap {
      override def transformTerm(tree: Term)(owner: Symbol): Term =
        tree match {
          case Inlined(_, _, term) => transformTerm(term)(owner)
          case Typed(term, _)      => transformTerm(term)(owner)
          case other               => super.transformTerm(other)(owner)
        }
    }

    val struct = Printer.TreeStructure.show(StripInlinedAndTyped.transformTerm(value.asTerm)(Symbol.spliceOwner))
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

  inline def extractTransformer[A](inline value: A): A = ${ extractTransformerMacro('value) }

  def extractTransformerMacro[A: Type](value: Expr[A])(using Quotes) = {
    import quotes.reflect.*
    given Printer[Tree] = Printer.TreeStructure

    val trans -> appliedTo = value match {
      case '{ ($transformer: Transformer[a, b]).transform($that) } =>
        transformer -> that
      case other => report.errorAndAbort(other.asTerm.show)
    }

    object traverser extends TreeMap {
      override def transformSubTrees[Tr <: Tree](trees: List[Tr])(owner: Symbol): List[Tr] = ???
    }


    val expr = trans.asTerm match {
      case Typed(Lambda(List(ValDef(arg, _, _)), Apply(call, args)), _) =>
        // report.info(term.show)
        val fieldNameds = args.map {
          case Select(Ident(`arg`), fieldName) => Select.unique(appliedTo.asTerm, fieldName)
          case other => other
        }
        Apply(call, fieldNameds)
      case tree  => 
        report.errorAndAbort(tree.show)
    }

    expr.asExprOf[A]
  } 
}
