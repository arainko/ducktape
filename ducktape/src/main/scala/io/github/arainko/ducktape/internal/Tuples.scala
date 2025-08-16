package io.github.arainko.ducktape.internal

import scala.annotation.tailrec
import scala.quoted.*

private[ducktape] object Tuples {

  object Named {
    def AnyNamedTuple(using Quotes): Option[Type[?]] = {
      import quotes.reflect.*
      Symbol.requiredModule("scala.NamedTuple").declaredType("AnyNamedTuple").headOption.map(_.typeRef.asType)
    } 
  }

  def unroll(tpe: Type[?])(using Quotes): List[quotes.reflect.TypeRepr] = {
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

    loop(tpe, Nil).reverse
  }

  def unrollStrings(using Quotes)(tp: quotes.reflect.TypeRepr): List[String] = {
    import quotes.reflect.*
    unroll(tp.asType).map { case ConstantType(StringConstant(l)) => l }
  }

  def rollup(using Quotes)(elements: Vector[quotes.reflect.TypeRepr]) = {
    import quotes.reflect.*

    elements.size match {
      case 0 => Type.of[EmptyTuple]
      case 1 =>
        elements.head.asType.match { case '[tpe] => Type.of[Tuple1[tpe]] }
      case size if size <= 22 =>
        defn
          .TupleClass(size)
          .typeRef
          .appliedTo(elements.toList)
          .asType
      case _ =>
        val TupleCons = TypeRepr.of[*:]
        val tpe = elements.foldRight(TypeRepr.of[EmptyTuple])((curr, acc) => TupleCons.appliedTo(curr :: acc :: Nil))
        tpe.asType
    }
  }
}
