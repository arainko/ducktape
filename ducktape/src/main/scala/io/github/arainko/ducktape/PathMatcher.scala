package io.github.arainko.ducktape

import scala.quoted.*
import io.github.arainko.ducktape.internal.macros.DebugMacros
import scala.collection.mutable.ListBuffer
import scala.annotation.tailrec

object PathMatcher {
  // inline def run[A](inline expr: Selector ?=> A => Any) = ${ readPath('expr) }

  def readPath[A: Type](expr: Expr[Selector ?=> A => Any])(using Quotes): List[String | Type[?]] = {
    import quotes.reflect.{ Selector as _, * }

    @tailrec
    def recurse(using Quotes)(acc: List[String | Type[?]], term: quotes.reflect.Term): List[String | Type[?]] = {
      import quotes.reflect.*

      given Printer[TypeRepr] = Printer.TypeReprShortCode

      term match {
        case Inlined(_, _, tree) =>
          recurse(acc, tree)
        case Lambda(_, tree) =>
          recurse(acc, tree)
        case Block(_, tree) =>
          recurse(acc, tree)
        case Select(tree, name) =>
          recurse(name :: acc, tree)
        case TypeApply(Apply(TypeApply(Select(Ident(_), "at"), _), tree :: Nil), tpe :: Nil) =>
          recurse(tpe.tpe.asType :: acc, tree)
        case Ident(_) => acc
        case other    => report.errorAndAbort(other.show(using Printer.TreeShortCode))
      }
    }
    recurse(Nil, expr.asTerm)
  }
}
