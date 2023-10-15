package io.github.arainko.ducktape.internal

import scala.quoted.*
import scala.compiletime.*
import scala.deriving.Mirror
import scala.collection.immutable.ListMap

private[ducktape] trait Debug[A] {
  extension (self: A) def show(using Quotes): String 
}

private[ducktape] object Debug {

  def show[A](value: A)(using Debug[A], Quotes) = value.show

  given string: Debug[String] with {
    extension (value: String) def show(using Quotes): String = s""""${value}""""
  }

  given wildcardTpe: Debug[Type[?]] with {
    extension (value: Type[?]) def show(using Quotes): String = {
      import quotes.reflect.*
      s"Type.of[${Printer.TypeReprShortCode.show(TypeRepr.of(using value))}]"
    }
  }

  given tpe[A]: Debug[Type[A]] with {
    extension (value: Type[A]) def show(using Quotes): String  = {
      import quotes.reflect.*
      s"Type.of[${Printer.TypeReprShortCode.show(TypeRepr.of(using value))}]"
    }
  }

  given collection[A, Coll[a] <: Iterable[a]](using debug: Debug[A]): Debug[Coll[A]] with {
    extension (value: Coll[A]) def show(using Quotes): String = value.map(debug.show).toString()
  }

  given map[K, V](using debugKey: Debug[K], debugValue: Debug[V]): Debug[Map[K, V]] with {
    extension (value: Map[K, V]) def show(using Quotes): String =
      value
        .map((key, value) => s"${debugKey.show(key)} -> ${debugValue.show(value)}")
        .mkString("Map(", ", ", ")")
  }

  given listMap[K, V](using debugKey: Debug[K], debugValue: Debug[V]): Debug[ListMap[K, V]] with {
    extension (value: ListMap[K, V]) def show(using Quotes): String =
      value
        .map((key, value) => s"${debugKey.show(key)} -> ${debugValue.show(value)}")
        .mkString("ListMap(", ", ", ")")
  }

  given option[A](using debug: Debug[A]): Debug[Option[A]] with {
    extension (self: Option[A]) def show(using Quotes): String = 
      self match {
        case None => "None"
        case Some(value) => s"Some(${value.show})"
      }
  }

  given term(using q: Quotes): Debug[q.reflect.Term] = new {
    extension (self: q.reflect.Term) def show(using Quotes): String = {
      import q.reflect.*
      s"""
      |  Structure: ${Printer.TreeStructure.show(self)}
      |  Code: ${Printer.TreeShortCode.show(self)}""".stripMargin
    }
  }

  given deferred[A]: Debug[() => A] with {
    extension (value: () => A) def show(using Quotes): String = 
      s"Deferred(...)"
  }

  given expr[A]: Debug[Expr[A]] with {
    extension (value: Expr[A]) def show(using Quotes): String = {
      import quotes.reflect.*
      s"Expr[${wildcardTpe.show(value.asTerm.tpe.asType)}]"
    }
  }

  inline def derived[A](using A: Mirror.Of[A]): Debug[A] =
    inline A match {
      case given Mirror.ProductOf[A] => product
      case given Mirror.SumOf[A] => coproduct
    }

  private inline def product[A](using A: Mirror.ProductOf[A]): Debug[A] =
    new {
      extension (value: A) def show(using Quotes): String = {
        val tpeName = constValue[A.MirroredLabel].toString
        val instances = summonAll[Tuple.Map[A.MirroredElemTypes, Debug]].toIArray.map(_.asInstanceOf[Debug[Any]])
        val prod = value.asInstanceOf[Product]
        val startParen = if (prod.productArity == 0) "" else "(" 
        val endParen = if (prod.productArity == 0) "" else ")"
        prod
          .productElementNames
          .zip(instances)
          .zip(prod.productIterator)
          .map { case label -> debug -> value => s"$label = ${debug.show(value)}"}
          .mkString(s"${tpeName}${startParen}", ", ", endParen)
      }
    }

  private inline def coproduct[A](using A: Mirror.SumOf[A]): Debug[A] = new {
    private val instances = deriveForAll[A.MirroredElemTypes].toVector

    extension (self: A) def show(using Quotes): String = {
      val ordinal = A.ordinal(self)
      instances(ordinal).show(self)
    }
  }

  private inline def deriveForAll[Tup <: Tuple]: List[Debug[Any]] =
    inline erasedValue[Tup] match {
      case _: (head *: tail) =>
        //TODO: Doesn't take into account existing instances, getting stack overflows when trying to do that for some reason
        derived[head](using summonInline[Mirror.Of[head]]).asInstanceOf[Debug[Any]] :: deriveForAll[tail]
      case _: EmptyTuple => Nil
    }

}
