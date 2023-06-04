package io.github.arainko.ducktape

import scala.quoted.*
import scala.annotation.tailrec
import scala.deriving.Mirror
import io.github.arainko.ducktape.Structure.Coproduct
import io.github.arainko.ducktape.Structure.Singleton
import io.github.arainko.ducktape.Structure.Ordinary
import io.github.arainko.ducktape.Structure.Lazy

sealed trait Structure {
  def tpe: Type[?]
  def name: String
  final def force: Structure =
    this match {
      case Lazy(struct) => struct().force
      case other        => other
    }
}

object Structure {
  case class Product(tpe: Type[?], name: String, fields: Map[String, Structure]) extends Structure
  case class Coproduct(tpe: Type[?], name: String, children: Vector[Structure]) extends Structure
  case class Singleton(tpe: Type[?], name: String, value: Expr[Any]) extends Structure
  case class Ordinary(tpe: Type[?], name: String) extends Structure
  case class Lazy(struct: () => Structure) extends Structure {
    private lazy val lazyStruct = struct()
    lazy val tpe = lazyStruct.tpe
    lazy val name = lazyStruct.name
  }

  inline def print[A] = ${ cos[A] }

  def cos[A: Type](using Quotes) = {
    import quotes.reflect.*
    report.info(of[A].toString())
    '{}
  }

  def of[A: Type](using Quotes): Structure = {
    import quotes.reflect.*

    Expr.summon[Mirror.Of[A]] match {
      case None => Structure.Ordinary(summon[Type[A]], Type.show[A]) // not really the short name
      case Some(value) =>
        value match {
          case '{
                type label <: String
                $m: Mirror.Singleton {
                  type MirroredLabel = `label`
                }
              } =>
            val value = Expr.summon[ValueOf[A]].get
            Structure.Singleton(summon[Type[A]], constantString[label], '{ $value.value })
          case '{
                type label <: String
                $m: Mirror.SingletonProxy {
                  type MirroredLabel = `label`
                }
              } =>
            val value = Expr.summon[ValueOf[A]].get
            Structure.Singleton(summon[Type[A]], constantString[label], '{ $value.value })
          case '{
                type label <: String
                $m: Mirror.Product {
                  type MirroredLabel = `label`
                  type MirroredElemLabels = labels
                  type MirroredElemTypes = types
                }
              } =>
            val structures =
              tupleTypeElements(TypeRepr.of[types]).map(tpe => tpe.asType match { case '[tpe] => Lazy(() => Structure.of[tpe]) })
            val names = constStringTuple(TypeRepr.of[labels])
            Structure.Product(summon[Type[A]], constantString[label], names.zip(structures).toMap)
          case '{
                type label <: String
                $m: Mirror.Sum {
                  type MirroredLabel = `label`
                  type MirroredElemTypes = types
                }
              } =>
            val structures =
              tupleTypeElements(TypeRepr.of[types]).map(tpe => tpe.asType match { case '[tpe] => Lazy(() => Structure.of[tpe]) })
            Structure.Coproduct(summon[Type[A]], constantString[label], structures.toVector)

        }
    }
  }

  private def constantString[Const <: String: Type](using Quotes) = Type.valueOfConstant[Const].get

  private def tupleTypeElements(using Quotes)(tp: quotes.reflect.TypeRepr): List[quotes.reflect.TypeRepr] = {
    import quotes.reflect.*

    @tailrec def loop(curr: TypeRepr, acc: List[TypeRepr]): List[TypeRepr] =
      curr match {
        case AppliedType(pairTpe, head :: tail :: Nil) => loop(tail, head :: acc)
        case _                                         => acc
      }
    loop(tp, Nil).reverse
  }

  private def constStringTuple(using Quotes)(tp: quotes.reflect.TypeRepr): List[String] = {
    import quotes.reflect.*
    tupleTypeElements(tp).map { case ConstantType(StringConstant(l)) => l }
  }
}
