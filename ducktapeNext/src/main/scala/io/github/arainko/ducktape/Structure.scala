package io.github.arainko.ducktape

import scala.quoted.*
import scala.annotation.tailrec
import scala.deriving.Mirror
import io.github.arainko.ducktape.Structure.Coproduct
import io.github.arainko.ducktape.Structure.Singleton
import io.github.arainko.ducktape.Structure.Ordinary
import io.github.arainko.ducktape.Structure.Lazy
import io.github.arainko.ducktape.internal.modules.*

sealed trait Structure {
  def tpe: Type[?]
  def name: String

  final def force: Structure =
    this match {
      case lzy: Lazy => lzy.struct.force
      case other     => other
    }
}

object Structure {
  def unapply(struct: Structure): (Type[?], String) = struct.tpe -> struct.name

  case class Product(tpe: Type[?], name: String, fields: Map[String, Structure]) extends Structure
  case class Coproduct(tpe: Type[?], name: String, children: Map[String, Structure]) extends Structure
  case class Singleton(tpe: Type[?], name: String, value: Expr[Any]) extends Structure
  case class Ordinary(tpe: Type[?], name: String) extends Structure
  case class ValueClass(tpe: Type[?], name: String, paramTpe: Type[?], paramFieldName: String) extends Structure
  case class Lazy(private val deferredStruct: () => Structure) extends Structure {
    lazy val struct = deferredStruct()
    lazy val tpe = struct.tpe
    lazy val name = struct.name
  }

  def of[A: Type](using Quotes): Structure = {
    import quotes.reflect.*
    given Printer[TypeRepr] = Printer.TypeReprShortCode

    Expr.summon[Mirror.Of[A]] match {
      case None =>
        summon[Type[A]].repr match {
          case valueClassRepr if valueClassRepr <:< TypeRepr.of[AnyVal] && valueClassRepr.typeSymbol.flags.is(Flags.Case) =>
            val param = valueClassRepr.typeSymbol.caseFields.head
            val paramTpe = valueClassRepr.memberType(param)
            Structure.ValueClass(summon[Type[A]], valueClassRepr.show, paramTpe.asType, param.name)
          case other =>
            Structure.Ordinary(summon[Type[A]], TypeRepr.of[A].show)
        }
      case Some(value) =>
        value match {
          case '{
                type label <: String
                $m: Mirror.Singleton {
                  type MirroredLabel = `label`
                }
              } =>
            val value = materializeSingleton[A]
            Structure.Singleton(summon[Type[A]], constantString[label], value.asExpr)
          case '{
                type label <: String
                $m: Mirror.SingletonProxy {
                  type MirroredLabel = `label`
                }
              } =>
            val value = materializeSingleton[A]
            Structure.Singleton(summon[Type[A]], constantString[label], value.asExpr)
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
                  type MirroredElemLabels = labels
                  type MirroredElemTypes = types
                }
              } =>
            val names = constStringTuple(TypeRepr.of[labels])
            val structures =
              tupleTypeElements(TypeRepr.of[types]).map(tpe => tpe.asType match { case '[tpe] => Lazy(() => Structure.of[tpe]) })

            Structure.Coproduct(summon[Type[A]], constantString[label], names.zip(structures).toMap)

        }
    }
  }

  private def materializeSingleton[A: Type](using Quotes) = {
    import quotes.reflect.*
    TypeRepr.of[A] match { case ref: TermRef => Ident(ref) }
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
