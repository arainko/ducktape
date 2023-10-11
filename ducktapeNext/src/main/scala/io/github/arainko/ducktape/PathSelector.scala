package io.github.arainko.ducktape

import scala.quoted.*
import io.github.arainko.ducktape.internal.macros.DebugMacros
import scala.collection.mutable.ListBuffer
import scala.annotation.tailrec

object PathSelector {
  def unapply(using Quotes)(expr: quotes.reflect.Term): Some[Path] = {
    import quotes.reflect.{ Selector as _, * }

    @tailrec
    def recurse(using Quotes)(acc: Path, term: quotes.reflect.Term): Path = {
      import quotes.reflect.*

      term match {
        case Inlined(_, _, tree) =>
          recurse(acc, tree)
        case Lambda(_, tree) =>
          recurse(acc, tree)
        case Block(_, tree) =>
          recurse(acc, tree)
        case select @ Select(tree, name) =>
          recurse(acc.prepended(Path.Segment.Field(select.tpe.asType, name)), tree)
        case TypeApply(Apply(TypeApply(Select(Ident(_), "at"), _), tree :: Nil), tpe :: Nil) =>
          recurse(acc.prepended(Path.Segment.Case(tpe.tpe.asType)), tree)
        // Function arg selection can only happen as the first selection (aka the last one to be parsed) so this not being recursive is fine (?)
        case TypeApply(Select(Apply(Select(Ident(_), "selectDynamic"), Literal(StringConstant(argName)) :: Nil), "$asInstanceOf$"), argTpe :: Nil) =>
          acc.prepended(Path.Segment.Field(argTpe.tpe.asType, argName))
        case Ident(_) => acc
        case other    => report.errorAndAbort(other.show(using Printer.TreeShortCode))
      }
    }

    Some(recurse(Path.empty, expr))
  }

}
