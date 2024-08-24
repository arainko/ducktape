package io.github.arainko.ducktape.internal

import scala.quoted.*
import scala.deriving.Mirror
import scala.annotation.tailrec

object MacroRepro {
  inline def iterateOverMirroredElemTypes[A](using A: Mirror.ProductOf[A]) = ${ impl('A) }

  def impl[A: Type](mirror: Expr[Mirror.ProductOf[A]])(using Quotes) = {
    import quotes.reflect.*

    mirror match {
      case '{
            $m: Mirror.Product {
              type MirroredElemLabels = labels
              type MirroredElemTypes = types
            }
          } =>
      tupleTypeElements(Type.of[types])
    }
  }

  private def tupleTypeElements(tpe: Type[?])(using Quotes) = {
    import quotes.reflect.*

    Logger.info(s"Tuple elems ${Type.show(using tpe)}")
    @tailrec def loop(using Quotes)(curr: Type[?], acc: List[quotes.reflect.TypeRepr]): List[quotes.reflect.TypeRepr] = {
      import quotes.reflect.*

      curr match {
        case '[head *: tail] =>
          loop(Type.of[tail], TypeRepr.of[head] :: acc)
        case '[EmptyTuple] =>
          acc
        case other =>
          report.errorAndAbort(
            s"Unexpected type (${other.repr.show}) encountered when extracting tuple type elems. This is a bug in ducktape."
          )
      }
    }

    val elems = loop(tpe, Nil).reverse
    report.info(elems.map(_.show(using Printer.TypeReprShortCode)).mkString(", "))
    '{}
  }
}
