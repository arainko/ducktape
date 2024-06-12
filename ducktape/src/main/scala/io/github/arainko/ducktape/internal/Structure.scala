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

  def path: Path

  final def force: Structure =
    this match {
      case lzy: Lazy => lzy.struct.force
      case other     => other
    }

  final def narrow[A <: Structure](using tt: TypeTest[Structure, A]): Option[A] = tt.unapply(this.force)
}

private[ducktape] object Structure {
  def toplevelAny(using Quotes) = Structure.Ordinary(Type.of[Any], Path.empty(Type.of[Any]))

  def toplevelNothing(using Quotes) = Structure.Ordinary(Type.of[Nothing], Path.empty(Type.of[Nothing]))

  case class Product(tpe: Type[?], path: Path, fields: ListMap[String, Structure], isTuple: Boolean) extends Structure {
    private var cachedDefaults: Map[String, Expr[Any]] = null

    def defaults(using Quotes): Map[String, Expr[Any]] =
      if cachedDefaults != null then cachedDefaults
      else {
        cachedDefaults = Defaults.of(this)
        cachedDefaults
      }
  }

  case class Coproduct(tpe: Type[?], path: Path, children: Map[String, Structure]) extends Structure

  case class Function(
    tpe: Type[?],
    path: Path,
    args: ListMap[String, Structure],
    function: io.github.arainko.ducktape.internal.Function
  ) extends Structure

  case class Optional(tpe: Type[? <: Option[?]], path: Path, paramStruct: Structure) extends Structure

  case class Collection(tpe: Type[? <: Iterable[?]], path: Path, paramStruct: Structure) extends Structure

  case class Singleton(tpe: Type[?], path: Path, name: String, value: Expr[Any]) extends Structure

  case class Ordinary(tpe: Type[?], path: Path) extends Structure

  case class ValueClass(tpe: Type[? <: AnyVal], path: Path, paramTpe: Type[?], paramFieldName: String) extends Structure

  case class Lazy private (tpe: Type[?], path: Path, private val deferredStruct: () => Structure) extends Structure {
    lazy val struct: Structure = deferredStruct()
  }

  object Lazy {
    def of[A: Type](path: Path)(using Quotes): Lazy = Lazy(Type.of[A], path, () => Structure.of[A](path))
  }

  def fromFunction(function: io.github.arainko.ducktape.internal.Function)(using Quotes): Structure.Function = {
    import quotes.reflect.*
    val path = Path.empty(function.returnTpe)

    val args =
      function.args.transform((name, tpe) =>
        tpe match { case '[argTpe] => Lazy.of[argTpe](path.appended(Path.Segment.Field(tpe, name))) }
      )

    Structure.Function(function.returnTpe, path, args, function)
  }

  def fromTypeRepr(using Quotes)(repr: quotes.reflect.TypeRepr, path: Path): Structure =
    repr.widen.asType match {
      case '[tpe] => Structure.of[tpe](path)name
    }

  def of[A: Type](path: Path)(using Quotes): Structure = {
    import quotes.reflect.*

    Logger.loggedInfo("Structure"):
      Type.of[A] match {
        case tpe @ '[Nothing] =>
          Structure.Ordinary(tpe, path)

        case tpe @ '[Option[param]] =>
          Structure.Optional(tpe, path, Structure.of[param](path.appended(Path.Segment.Element(Type.of[param]))))

        case tpe @ '[Iterable[param]] =>
          Structure.Collection(tpe, path, Structure.of[param](path.appended(Path.Segment.Element(Type.of[param]))))

        case tpe @ '[AnyVal] if tpe.repr.typeSymbol.flags.is(Flags.Case) =>
          val repr = tpe.repr
          val param = repr.typeSymbol.caseFields.head
          val paramTpe = repr.memberType(param)
          Structure.ValueClass(tpe, path, paramTpe.asType, param.name)

        case _ =>
          Expr.summon[Mirror.Of[A]] match {
            case None =>
              Structure.Ordinary(Type.of[A], path)

            case Some(value) =>
              value match {
                case '{
                      type label <: String
                      $m: Mirror.Singleton {
                        type MirroredLabel = `label`
                      }
                    } =>
                  val value = materializeSingleton[A]
                  Structure.Singleton(Type.of[A], path, constantString[label], value.asExpr)
                case '{
                      type label <: String
                      $m: Mirror.SingletonProxy {
                        type MirroredLabel = `label`
                      }
                    } =>
                  val value = materializeSingleton[A]
                  Structure.Singleton(Type.of[A], path, constantString[label], value.asExpr)
                case '{
                      $m: Mirror.Product {
                        type MirroredElemLabels = labels
                        type MirroredElemTypes = types
                      }
                    } =>
                  val structures =
                    tupleTypeElements(TypeRepr.of[types])
                      .zip(constStringTuple(TypeRepr.of[labels]))
                      .map((tpe, name) =>
                        name -> (tpe.asType match {
                          case '[tpe] => Lazy.of[tpe](path.appended(Path.Segment.Field(Type.of[tpe], name)))
                        })
                      )
                      .to(ListMap)

                  
                  Structure.Product(Type.of[A], path, structures, Type.of[A].repr.isTupleN)
                case '{
                      $m: Mirror.Sum {
                        type MirroredElemLabels = labels
                        type MirroredElemTypes = types
                      }
                    } =>
                  val structures =
                    tupleTypeElements(TypeRepr.of[types])
                      .zip(constStringTuple(TypeRepr.of[labels]))
                      .map((tpe, name) =>
                        name -> (tpe.asType match { case '[tpe] => Lazy.of[tpe](path.appended(Path.Segment.Case(Type.of[tpe]))) })
                      )
                      .toMap

                  Structure.Coproduct(Type.of[A], path, structures)
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
