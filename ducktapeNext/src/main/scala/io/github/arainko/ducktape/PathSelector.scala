package io.github.arainko.ducktape

import scala.quoted.*
import io.github.arainko.ducktape.internal.macros.DebugMacros
import scala.collection.mutable.ListBuffer
import scala.annotation.tailrec

object PathSelector {
  // inline def run[A](inline expr: Selector ?=> A => Any) = ${ readPath('expr) }

  def unapply(using Quotes)(expr: quotes.reflect.Term): Option[Path.NonEmpty] = {
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
        case Ident(_) => acc
        case other    => report.errorAndAbort(other.show(using Printer.TreeShortCode))
      }
    }

    Path.NonEmpty.fromPath(recurse(Path.empty, expr))
  }

}