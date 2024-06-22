package io.github.arainko.ducktape.internal

import scala.compiletime.*
import scala.deriving.Mirror
import scala.quoted.*
import scala.reflect.ClassTag
import scala.collection.immutable.ListMap

private[ducktape] trait Debug[-A] {
  def astify(self: A)(using Quotes): Debug.AST

  extension (self: A) final def show(using Quotes): String = astify(self).dropEmpty.show
}

private[ducktape] object Debug extends LowPriorityDebug {
  import AST.*

  val nonShowable: Debug[Any] = new:
    def astify(self: Any)(using Quotes): AST = Empty

  def show[A](value: A)(using Debug[A], Quotes) = value.show

  given string: Debug[String] with {
    override def astify(self: String)(using Quotes): AST = Text(s""""${self}"""")
  }

  given int: Debug[Int] with {
    override def astify(self: Int)(using Quotes): AST = Text(self.toString)
  }

  given bool: Debug[Boolean] with {
    override def astify(self: Boolean)(using Quotes): AST = Text(self.toString)
  }

  given wildcardTpe: Debug[Type[?]] with {
    override def astify(self: Type[?])(using Quotes): AST = {
      import quotes.reflect.*
      Text(s"Type.of[${Printer.TypeReprShortCode.show(TypeRepr.of(using self))}]")
    }
  }

  given tpe[A]: Debug[Type[A]] with {
    def astify(self: Type[A])(using Quotes): AST = {
      import quotes.reflect.*
      Text(s"Type.of[${Printer.TypeReprShortCode.show(TypeRepr.of(using self))}]")
    }
  }

  given collection[A, Coll[a] <: Iterable[a]](using debug: Debug[A], tag: ClassTag[Coll[A]]): Debug[Coll[A]] with {
    def astify(self: Coll[A])(using Quotes): AST = {
      val name = tag.runtimeClass.getSimpleName()
      Collection(name, self.map(debug.astify).toVector)
    }
  }

  given map[K, V](using debugKey: Debug[K], debugValue: Debug[V]): Debug[Map[K, V]] with {

    def astify(self: Map[K, V])(using Quotes): AST = {
      Collection(
        "Map",
        self
          .map((key, value) => Product("Entry", ListMap("key" -> debugKey.astify(key), "value" -> debugValue.astify(value))))
          .toVector
      )
    }
  }

  given option[A](using debug: Debug[A]): Debug[Option[A]] with {
    def astify(self: Option[A])(using Quotes): AST =
      self match
        case None        => Text("None")
        case Some(value) => Collection("Some", Vector(debug.astify(value)))
  }

  given term(using q: Quotes): Debug[q.reflect.Term] = new {

    def astify(self: q.reflect.Term)(using Quotes): AST = {
      import q.reflect.*
      Text {
        s"""
          |  Structure: ${Printer.TreeStructure.show(self)}
          |  Code: ${Printer.TreeShortCode.show(self)}""".stripMargin
      }
    }

  }

  given deferred: Debug[() => Any] = Debug.nonShowable

  given expr[A]: Debug[Expr[A]] with {

    def astify(self: Expr[A])(using Quotes): AST = {
      import quotes.reflect.*
      Text(s"Expr[${self.asTerm.tpe.show(using Printer.TypeReprShortCode)}]")
    }
  }

  inline def derived[A](using A: Mirror.Of[A]): Debug[A] =
    inline A match {
      case given Mirror.ProductOf[A] => product
      case given Mirror.SumOf[A]     => coproduct
    }

  private inline def product[A](using A: Mirror.ProductOf[A]): Debug[A] =
    new {
      def astify(self: A)(using Quotes): AST = {
        val tpeName = constValue[A.MirroredLabel].toString
        val instances = summonAll[Tuple.Map[A.MirroredElemTypes, Debug]].toIArray.map(_.asInstanceOf[Debug[Any]])
        val prod = self.asInstanceOf[scala.Product]
        val fields = prod.productElementNames
          .zip(instances)
          .zip(prod.productIterator)
          .map {
            case label -> debug -> value =>
              label -> debug.astify(value)
          }
          .to(ListMap)

        Product(tpeName, fields)
      }
    }

  private inline def coproduct[A](using A: Mirror.SumOf[A]): Debug[A] = new {
    private val instances = deriveForAll[A.MirroredElemTypes].toVector

    def astify(self: A)(using Quotes): AST = {
      val ordinal = A.ordinal(self)
      instances(ordinal).astify(self)
    }
  }

  private inline def deriveForAll[Tup <: Tuple]: List[Debug[Any]] =
    inline erasedValue[Tup] match {
      case _: (head *: tail) =>
        // TODO: Doesn't take into account existing instances, getting stack overflows when trying to do that for some reason
        derived[head](using summonInline[Mirror.Of[head]]).asInstanceOf[Debug[Any]] :: deriveForAll[tail]
      case _: EmptyTuple => Nil
    }

  enum AST {
    case Empty
    case Text(value: String)
    case Product(name: String, fields: ListMap[String, AST])
    case Collection(name: String, values: Vector[AST])

    final def dropEmpty: AST =
      this match
        case Empty                 => Empty
        case t @ Text(_)           => t
        case Product(name, fields) => Product(name, fields.collect { case (name, ast) if ast != Empty => name -> ast.dropEmpty })
        case Collection(name, values) => Collection(name, values.collect { case ast if ast != Empty => ast.dropEmpty })

    final def length: Int =
      this match
        case Empty                    => 0
        case Text(value)              => value.length
        case Product(name, fields)    => name.length + fields.map((name, ast) => name.length + ast.length).sum
        case Collection(name, values) => name.length + values.map(_.length).sum

    final def show: String = {
      def ident(n: Int) = "  " * n

      // if you think this is over then you're wrong
      val Separator = System.lineSeparator

      def recurse(ast: AST, depth: Int): String = {
        ast match
          case Empty       => ""
          case Text(value) => value
          case p @ Product(name, fields) =>
            if (p.length >= 80) {
              s"$name(".bold + Separator +
                fields.map { (name, ast) =>
                  ident(depth + 1) + name.yellow +  " = ".yellow + recurse(ast, depth + 1)
                }.mkString("," + Separator) + Separator + ident(depth) + ")".bold
            } else {
              name.bold + "(".bold + fields.map((name, ast) => name.yellow + " = ".yellow + recurse(ast, 0)).mkString(", ") + ")".bold
            }

          case c @ Collection(name, values) =>
            if (c.length >= 80) {
              s"$name(".bold + Separator +
                values.map(value => ident(depth + 1) + recurse(value, depth + 1)).mkString("," + Separator) + Separator + ident(
                  depth
                ) + ")".bold
            } else {
              s"$name(".bold + values.map(recurse(_, 0)).mkString(", ") + ")".bold
            }
      }
      recurse(this, 0)
    }

    extension (self: String) {
      private def bold: String = s"${Console.BOLD}$self${Console.RESET}"
      private def yellow: String = s"${Console.YELLOW}$self${Console.RESET}"
    }
  }
}

private[ducktape] transparent trait LowPriorityDebug {
  given Debug[Nothing => Any] = Debug.nonShowable
}
