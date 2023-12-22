package io.github.arainko.ducktape.internal

import io.github.arainko.ducktape.internal.Structure.*
import io.github.arainko.ducktape.internal.*

import scala.annotation.tailrec
import scala.collection.immutable.ListMap
import scala.deriving.Mirror
import scala.quoted.*
import scala.reflect.TypeTest
import scala.annotation.threadUnsafe

private[ducktape] sealed trait Structure derives Debug {
  def tpe: Type[?]

  def parent: Structure.Lazy | None.type

  final def force: Structure =
    this match {
      case lzy: Lazy => lzy.struct.force
      case other     => other
    }

  final def narrow[A <: Structure](using tt: TypeTest[Structure, A]): Option[A] = tt.unapply(this.force)
}

private[ducktape] object Structure {
  private given Debug[Structure.Lazy | None.type] = new:
    extension (self: Lazy | None.type)
      def show(using Quotes): String =
        self match {
          case lzy: Lazy => "Lazy(...)"
          case None      => "None"
        }

  case class Product(tpe: Type[?], parent: Structure.Lazy | None.type, fields: Map[String, Structure]) extends Structure

  case class Coproduct(tpe: Type[?], parent: Structure.Lazy | None.type, children: Map[String, Structure]) extends Structure

  case class Function(
    tpe: Type[?],
    parent: Structure.Lazy | None.type,
    args: ListMap[String, Structure],
    function: io.github.arainko.ducktape.internal.Function
  ) extends Structure

  case class Optional(tpe: Type[? <: Option[?]], parent: Structure.Lazy | None.type, paramStruct: Structure) extends Structure

  case class Collection(tpe: Type[? <: Iterable[?]], parent: Structure.Lazy | None.type, paramStruct: Structure) extends Structure

  case class Singleton(tpe: Type[?], parent: Structure.Lazy | None.type, name: String, value: Expr[Any]) extends Structure

  case class Ordinary(tpe: Type[?], parent: Structure.Lazy | None.type) extends Structure

  case class ValueClass(
    tpe: Type[? <: AnyVal],
    parent: Structure.Lazy | None.type,
    paramTpe: Type[?],
    paramFieldName: String
  ) extends Structure

  case class Lazy(private val deferredStruct: () => Structure) extends Structure {
    @threadUnsafe lazy val struct: Structure = deferredStruct()
    @threadUnsafe lazy val tpe: Type[?] = struct.tpe
    @threadUnsafe lazy val parent: Lazy | None.type = struct.parent
  }

  def fromFunction(function: io.github.arainko.ducktape.internal.Function)(using Quotes): Structure.Function = {
    import quotes.reflect.*

    def args(parent: () => Structure) =
      function.args.transform((name, tpe) => tpe match { case '[argTpe] => Lazy(() => Structure.of[argTpe](Lazy(parent))) })

    @threadUnsafe 
    lazy val struct: Structure.Function = Structure.Function(function.returnTpe, None, args(() => struct), function)
    struct
  }

  def fromTypeRepr(using Quotes)(repr: quotes.reflect.TypeRepr): Structure =
    repr.widen.asType match {
      case '[tpe] => Structure.of[tpe](None)
    }

  def of[A: Type](parent: Structure.Lazy | None.type)(using Quotes): Structure = {
    import quotes.reflect.*

    Logger.loggedInfo("Structure"):
      Type.of[A] match {
        case tpe @ '[Option[param]] =>
          @threadUnsafe 
          lazy val struct: Structure = Structure.Optional(tpe, parent, Structure.of[param](Lazy(() => struct)))
          struct

        case tpe @ '[Iterable[param]] =>
          @threadUnsafe 
          lazy val struct: Structure = Structure.Collection(tpe, parent, Structure.of[param](Lazy(() => struct)))
          struct

        case tpe @ '[AnyVal] if tpe.repr.typeSymbol.flags.is(Flags.Case) =>
          val repr = tpe.repr
          val param = repr.typeSymbol.caseFields.head
          val paramTpe = repr.memberType(param)
          Structure.ValueClass(tpe, parent, paramTpe.asType, param.name)

        case _ =>
          Expr.summon[Mirror.Of[A]] match {
            case None =>
              Logger.info("It's ordinary")
              Structure.Ordinary(Type.of[A], parent)

            case Some(value) =>
              value match {
                case '{
                      type label <: String
                      $m: Mirror.Singleton {
                        type MirroredLabel = `label`
                      }
                    } =>
                  val value = materializeSingleton[A]
                  Structure.Singleton(Type.of[A], parent, constantString[label], value.asExpr)
                case '{
                      type label <: String
                      $m: Mirror.SingletonProxy {
                        type MirroredLabel = `label`
                      }
                    } =>
                  val value = materializeSingleton[A]
                  Structure.Singleton(Type.of[A], parent, constantString[label], value.asExpr)

                case '{
                      $m: Mirror.Product {
                        type MirroredElemLabels = labels
                        type MirroredElemTypes = types
                      }
                    } =>
                  def structures(parent: () => Structure) =
                    tupleTypeElements(TypeRepr.of[types]).map(tpe =>
                      tpe.asType match { case '[tpe] => Lazy(() => Structure.of[tpe](Lazy(parent))) }
                    )
                  val names = constStringTuple(TypeRepr.of[labels])

                  @threadUnsafe 
                  lazy val struct: Structure = Structure.Product(Type.of[A], parent, names.zip(structures(() => struct)).toMap)
                  struct

                case '{
                      $m: Mirror.Sum {
                        type MirroredElemLabels = labels
                        type MirroredElemTypes = types
                      }
                    } =>
                  val names = constStringTuple(TypeRepr.of[labels])
                  def structures(parent: () => Structure) =
                    tupleTypeElements(TypeRepr.of[types]).map(tpe =>
                      tpe.asType match { case '[tpe] => Lazy(() => Structure.of[tpe](Lazy(parent))) }
                    )
                  
                  @threadUnsafe 
                  lazy val struct: Structure = Structure.Coproduct(Type.of[A], parent, names.zip(structures(() => struct)).toMap)
                  struct
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
