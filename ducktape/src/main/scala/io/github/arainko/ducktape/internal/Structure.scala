package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.internal.Structure.*
import io.github.arainko.ducktape.internal.*

import scala.annotation.tailrec
import scala.collection.immutable.ListMap
import scala.deriving.Mirror
import scala.quoted.*
import scala.reflect.TypeTest

private[ducktape] sealed trait Structure derives Debug {
  def tpe: Type[?]

  final def force: Structure =
    this match {
      case lzy: Lazy => lzy.struct.force
      case other     => other
    }

  final def narrow[A <: Structure](using tt: TypeTest[Structure, A]): Option[A] = tt.unapply(this.force)
}

private[ducktape] object Structure {
  case class Product(tpe: Type[?], fields: Map[String, Structure]) extends Structure

  case class Coproduct(tpe: Type[?], children: Map[String, Structure]) extends Structure

  case class Function(
    tpe: Type[?],
    args: ListMap[String, Structure],
    function: io.github.arainko.ducktape.internal.Function
  ) extends Structure

  case class Optional(tpe: Type[? <: Option[?]], paramStruct: Structure) extends Structure

  case class Collection(tpe: Type[? <: Iterable[?]], paramStruct: Structure) extends Structure

  case class Singleton(tpe: Type[?], name: String, value: Expr[Any]) extends Structure

  case class Ordinary(tpe: Type[?]) extends Structure

  case class ValueClass(tpe: Type[? <: AnyVal], paramTpe: Type[?], paramFieldName: String) extends Structure

  case class Lazy private (tpe: Type[?], private val deferredStruct: () => Structure) extends Structure {
    lazy val struct = deferredStruct()
  }

  object Lazy {
    def of[A: Type](using Quotes): Lazy = Lazy(Type.of[A], () => Structure.of[A])
  }

  def fromFunction(function: io.github.arainko.ducktape.internal.Function)(using Quotes): Structure.Function = {
    import quotes.reflect.*

    val args =
      function.args.transform((name, tpe) => tpe match { case '[argTpe] => Lazy.of[argTpe] })

    Structure.Function(function.returnTpe, args, function)
  }

  def fromTypeRepr(using Quotes)(repr: quotes.reflect.TypeRepr): Structure =
    repr.widen.asType match {
      case '[tpe] => Structure.of[tpe]
    }

  def of[A: Type](using Quotes): Structure = {
    import quotes.reflect.*

    Logger.loggedInfo("Structure"):
      Type.of[A] match {
        case tpe @ '[Nothing] =>
          Structure.Ordinary(tpe)

        case tpe @ '[Option[param]] =>
          Structure.Optional(tpe, Structure.of[param])

        case tpe @ '[Iterable[param]] =>
          Structure.Collection(tpe, Structure.of[param])

        case tpe @ '[AnyVal] if tpe.repr.typeSymbol.flags.is(Flags.Case) =>
          val repr = tpe.repr
          val param = repr.typeSymbol.caseFields.head
          val paramTpe = repr.memberType(param)
          Structure.ValueClass(tpe, paramTpe.asType, param.name)

        case _ =>
          Expr.summon[Mirror.Of[A]] match {
            case None =>
              Structure.Ordinary(Type.of[A])

            case Some(value) =>
              value match {
                case '{
                      type label <: String
                      $m: Mirror.Singleton {
                        type MirroredLabel = `label`
                      }
                    } =>
                  val value = materializeSingleton[A]
                  Structure.Singleton(Type.of[A], constantString[label], value.asExpr)
                case '{
                      type label <: String
                      $m: Mirror.SingletonProxy {
                        type MirroredLabel = `label`
                      }
                    } =>
                  val value = materializeSingleton[A]
                  Structure.Singleton(Type.of[A], constantString[label], value.asExpr)
                case '{
                      $m: Mirror.Product {
                        type MirroredElemLabels = labels
                        type MirroredElemTypes = types
                      }
                    } =>
                  val structures =
                    tupleTypeElements(TypeRepr.of[types]).map(tpe =>
                      tpe.asType match { case '[tpe] => Lazy.of[tpe] }
                    )
                  val names = constStringTuple(TypeRepr.of[labels])
                  Structure.Product(Type.of[A], names.zip(structures).toMap)
                case '{
                      $m: Mirror.Sum {
                        type MirroredElemLabels = labels
                        type MirroredElemTypes = types
                      }
                    } =>
                  val names = constStringTuple(TypeRepr.of[labels])
                  val structures =
                    tupleTypeElements(TypeRepr.of[types]).map(tpe =>
                      tpe.asType match { case '[tpe] => Lazy.of[tpe] }
                    )

                  Structure.Coproduct(Type.of[A], names.zip(structures).toMap)
              }
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
