package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.internal.*
import io.github.arainko.ducktape.internal.Structure.*

import scala.annotation.{ tailrec, unused }
import scala.collection.immutable.VectorMap
import scala.deriving.Mirror
import scala.quoted.*
import scala.reflect.TypeTest

private[ducktape] sealed trait Structure extends scala.Product derives Debug {
  def tpe: Type[?]

  def path: Path

  def _1: Type[?] = tpe

  final def force: Structure =
    this match {
      case lzy: Lazy => lzy.struct.force
      case other     => other
    }

  final def narrow[A <: Structure](using tt: TypeTest[Structure, A]): Option[A] = tt.unapply(this.force)
}

private[ducktape] object Structure {
  def unapply(struct: Structure): Structure = struct

  def toplevelAny(using Quotes) = Structure.Ordinary(Type.of[Any], Path.empty(Type.of[Any]))

  def toplevelNothing(using Quotes) = Structure.Ordinary(Type.of[Nothing], Path.empty(Type.of[Nothing]))

  case class Product(tpe: Type[?], path: Path, fields: VectorMap[String, Structure]) extends Structure {
    private var cachedDefaults: Map[String, Expr[Any]] = null

    def defaults(using Quotes): Map[String, Expr[Any]] =
      if cachedDefaults != null then cachedDefaults
      else {
        cachedDefaults = Defaults.of(this)
        cachedDefaults
      }
  }

  case class Tuple(tpe: Type[?], path: Path, elements: Vector[Structure], isPlain: Boolean) extends Structure

  case class Coproduct(tpe: Type[?], path: Path, children: Map[String, Structure]) extends Structure

  case class Function(
    tpe: Type[?],
    path: Path,
    args: VectorMap[String, Structure],
    function: io.github.arainko.ducktape.internal.Function
  ) extends Structure

  case class Optional(tpe: Type[? <: Option[?]], path: Path, paramStruct: Structure) extends Structure

  object Optional {
    def fromWrapped(wrapped: Wrapped[Option]): Optional =
      Optional(wrapped.tpe, wrapped.path, wrapped.underlying)
  }

  case class Collection(tpe: Type[? <: Iterable[?]], path: Path, paramStruct: Structure) extends Structure

  case class Singleton(tpe: Type[?], path: Path, name: String, value: Expr[Any]) extends Structure

  case class Ordinary(tpe: Type[?], path: Path) extends Structure

  case class ValueClass(tpe: Type[? <: AnyVal], path: Path, paramTpe: Type[?], paramFieldName: String) extends Structure

  case class Wrapped[F[+x]](tpe: Type[? <: F[Any]], wrapper: WrapperType[F], path: Path, underlying: Structure) extends Structure

  case class Lazy private (tpe: Type[?], path: Path, private val deferredStruct: () => Structure) extends Structure {
    lazy val struct: Structure = deferredStruct()
  }

  object Lazy {
    def of[A: Type](path: Path)(using Context, Quotes): Lazy = Lazy(Type.of[A], path, () => Structure.of[A](path))
  }

  def fromFunction(function: io.github.arainko.ducktape.internal.Function)(using Context, Quotes): Structure.Function = {
    import quotes.reflect.*
    val path = Path.empty(function.returnTpe)

    val args =
      function.args.transform((name, tpe) =>
        tpe match { case '[argTpe] => Lazy.of[argTpe](path.appended(Path.Segment.Field(tpe, name))) }
      )

    Structure.Function(function.returnTpe, path, args, function)
  }

  def fromTypeRepr(using Context, Quotes)(repr: quotes.reflect.TypeRepr, path: Path): Structure =
    repr.widen.asType match {
      case '[tpe] => Structure.of[tpe](path)
    }

  def of[A: Type](path: Path)(using Context, Quotes): Structure = {
    import quotes.reflect.*

    Logger.loggedInfo("Structure"):
      Type.of[A] match {
        case tpe @ '[Nothing] =>
          Structure.Ordinary(tpe, path)

        case WrapperType(wrapper: WrapperType[f], '[underlying]) =>
          @unused given Type[f] = wrapper.wrapper
          Structure.Wrapped(
            Type.of[f[underlying]],
            wrapper,
            path,
            Structure.of[underlying](path.appended(Path.Segment.Element(Type.of[underlying])))
          )

        case tpe @ '[Option[param]] =>
          Structure.Optional(tpe, path, Structure.of[param](path.appended(Path.Segment.Element(Type.of[param]))))

        case tpe @ '[Iterable[param]] =>
          Structure.Collection(tpe, path, Structure.of[param](path.appended(Path.Segment.Element(Type.of[param]))))

        case tpe @ '[AnyVal] if tpe.repr.typeSymbol.flags.is(Flags.Case) =>
          val repr = tpe.repr
          val param = repr.typeSymbol.caseFields.head
          val paramTpe = repr.memberType(param)
          Structure.ValueClass(tpe, path, paramTpe.asType, param.name)

        case tpe @ '[Any *: scala.Tuple] if !tpe.repr.isTupleN => // let plain tuples be caught later on
          val elements =
            tupleTypeElements(tpe).zipWithIndex.map { (tpe, idx) =>
              tpe.asType match {
                case '[tpe] => Lazy.of[tpe](path.appended(Path.Segment.TupleElement(Type.of[tpe], idx)))
              }
            }.toVector
          Structure.Tuple(Type.of[A], path, elements, isPlain = false)

        case tpe =>
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
                    } if tpe.repr.isTupleN =>
                  val structures =
                    tupleTypeElements(Type.of[types]).zipWithIndex
                      .map((tpe, idx) =>
                        tpe.asType match {
                          case '[tpe] => Lazy.of[tpe](path.appended(Path.Segment.TupleElement(Type.of[tpe], idx)))
                        }
                      )
                      .toVector

                  Structure.Tuple(Type.of[A], path, structures, isPlain = true)

                case '{
                      $m: Mirror.Product {
                        type MirroredElemLabels = labels
                        type MirroredElemTypes = types
                      }
                    } =>
                  val structures =
                    tupleTypeElements(Type.of[types])
                      .zip(constStringTuple(TypeRepr.of[labels]))
                      .map((tpe, name) =>
                        name -> (tpe.asType match {
                          case '[tpe] => Lazy.of[tpe](path.appended(Path.Segment.Field(Type.of[tpe], name)))
                        })
                      )
                      .to(VectorMap)

                  Structure.Product(Type.of[A], path, structures)
                case '{
                      $m: Mirror.Sum {
                        type MirroredElemLabels = labels
                        type MirroredElemTypes = types
                      }
                    } =>
                  val structures =
                    tupleTypeElements(Type.of[types])
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

  private def tupleTypeElements(tpe: Type[?])(using Quotes): List[quotes.reflect.TypeRepr] = {
    Logger.info(s"Tuple elems ${Type.show(using tpe)}")
    @tailrec def loop(using Quotes)(curr: Type[?], acc: List[quotes.reflect.TypeRepr]): List[quotes.reflect.TypeRepr] = {
      import quotes.reflect.*

      curr match {
        case '[head *: tail] =>
          Logger.info("Type.show:" + Type.show[head])
          Logger.info("TypeRepr.show:" + TypeRepr.of[head].show)

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

  private def constStringTuple(using Quotes)(tp: quotes.reflect.TypeRepr): List[String] = {
    import quotes.reflect.*
    tupleTypeElements(tp.asType).map { case ConstantType(StringConstant(l)) => l }
  }
}
