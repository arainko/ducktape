package io.github.arainko.ducktape.internal

import scala.annotation.tailrec
import scala.quoted.*

object PathSelector {
  def unapply(using Quotes)(expr: quotes.reflect.Term): Some[Path] = {
    import quotes.reflect.{ Selector as _, * }

    @tailrec
    def recurse(using Quotes)(acc: List[Path.Segment], term: quotes.reflect.Term): Path = {
      import quotes.reflect.*

      term match {
        case Inlined(_, _, tree) =>
          Logger.debug("Matched 'Inlined', recursing...")
          recurse(acc, tree)
        case Lambda(_, tree) =>
          Logger.debug("Matched 'Lambda', recursing...")
          recurse(acc, tree)
        case Block(_, tree) =>
          Logger.debug("Matched 'Block', recursing...")
          recurse(acc, tree)
        case select @ Select(tree, name) =>
          Logger.debug(s"Matched 'Select' (matching field access) with name = $name")
          recurse(acc.prepended(Path.Segment.Field(select.tpe.asType, name)), tree)
        case TypeApply(Apply(TypeApply(Select(Ident(_), "at"), _), tree :: Nil), tpe :: Nil) =>
          Logger.debug(s"Matched 'TypeApply' (matching '.at')", tpe.tpe.asType)
          recurse(acc.prepended(Path.Segment.Case(tpe.tpe.asType)), tree)
        // Function arg selection can only happen as the first selection (aka the last one to be parsed) so this not being recursive is fine (?)
        case TypeApply(
              Select(Apply(Select(ident @ Ident(_), "selectDynamic"), Literal(StringConstant(argName)) :: Nil), "$asInstanceOf$"),
              argTpe :: Nil
            ) =>
          Logger.debug(s"Matched 'selectDynamic' (matching a function arg selector) with name = $argName")
          Path(ident.tpe.asType, acc.prepended(Path.Segment.Field(argTpe.tpe.asType, argName)).toVector)
        case ident @ Ident(_) =>
          Logger.debug(s"Matched 'Ident', returning...")
          Path(ident.tpe.asType, acc.toVector)
        case other =>
          Logger.debug(s"Matched an unexpected term")
          report.errorAndAbort(other.show(using Printer.TreeShortCode))
      }
    }

    Some(Logger.loggedInfo("Parsed path")(recurse(Nil, expr)))
  }

}
