package io.github.arainko.ducktape.internal.modules

import scala.annotation.tailrec
import scala.deriving.Mirror
import scala.quoted.*

private[ducktape] final class MaterializedMirror[Q <: Quotes & Singleton] private (using val quotes: Q)(
  val mirroredType: quotes.reflect.TypeRepr,
  val mirroredMonoType: quotes.reflect.TypeRepr,
  val mirroredElemTypes: List[quotes.reflect.TypeRepr],
  val mirroredLabel: String,
  val mirroredElemLabels: List[String]
)

// Lifted from shapeless 3:
// https://github.com/typelevel/shapeless-3/blob/main/modules/deriving/src/main/scala/shapeless3/deriving/internals/reflectionutils.scala
private[ducktape] object MaterializedMirror {

  def createOrAbort[A: Type](mirror: Expr[Mirror.Of[A]])(using Quotes): MaterializedMirror[quotes.type] =
    create(mirror).fold(memberName => Failure.abort(Failure.MirrorMaterialization(summon, memberName)), identity)

  private def create(mirror: Expr[Mirror])(using Quotes): Either[String, MaterializedMirror[quotes.type]] = {
    import quotes.reflect.*

    val mirrorTpe = mirror.asTerm.tpe.widen
    for {
      mirroredType <- findMemberType(mirrorTpe, "MirroredType")
      mirroredMonoType <- findMemberType(mirrorTpe, "MirroredMonoType")
      mirroredElemTypes <- findMemberType(mirrorTpe, "MirroredElemTypes")
      mirroredLabel <- findMemberType(mirrorTpe, "MirroredLabel")
      mirroredElemLabels <- findMemberType(mirrorTpe, "MirroredElemLabels")
    } yield {
      val elemTypes = tupleTypeElements(mirroredElemTypes)
      val ConstantType(StringConstant(label)) = mirroredLabel: @unchecked
      val elemLabels = tupleTypeElements(mirroredElemLabels).map { case ConstantType(StringConstant(l)) => l }
      MaterializedMirror(mirroredType, mirroredMonoType, elemTypes, label, elemLabels)
    }
  }

  private def tupleTypeElements(using Quotes)(tp: quotes.reflect.TypeRepr): List[quotes.reflect.TypeRepr] = {
    import quotes.reflect.*

    @tailrec def loop(tp: TypeRepr, acc: List[TypeRepr]): List[TypeRepr] = tp match {
      case AppliedType(pairTpe, List(hd: TypeRepr, tl: TypeRepr)) => loop(tl, hd :: acc)
      case _                                                      => acc
    }
    loop(tp, Nil).reverse
  }

  private def low(using Quotes)(tp: quotes.reflect.TypeRepr): quotes.reflect.TypeRepr = {
    import quotes.reflect.*

    tp match {
      case tp: TypeBounds => tp.low
      case tp             => tp
    }
  }

  private def findMemberType(using Quotes)(tp: quotes.reflect.TypeRepr, name: String): Either[String, quotes.reflect.TypeRepr] = {
    import quotes.reflect.*

    tp match {
      case Refinement(_, `name`, tp) => Right(low(tp))
      case Refinement(parent, _, _)  => findMemberType(parent, name)
      case AndType(left, right)      => findMemberType(left, name).orElse(findMemberType(right, name))
      case _                         => Left(name)
    }
  }

}
