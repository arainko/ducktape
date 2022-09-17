package io.github.arainko.ducktape.internal.modules

import scala.quoted.*
import scala.annotation.tailrec
import scala.deriving.Mirror

// Lifted from shapeless 3:
// https://github.com/typelevel/shapeless-3/blob/main/modules/deriving/src/main/scala/shapeless3/deriving/internals/reflectionutils.scala
private[internal] trait MirrorModule { self: Module =>
  import quotes.reflect.*

  final case class MaterializedMirror private (
    mirroredType: TypeRepr,
    mirroredMonoType: TypeRepr,
    mirroredElemTypes: List[TypeRepr],
    mirroredLabel: String,
    mirroredElemLabels: List[String]
  )

  object MaterializedMirror {
    def createOrAbort[A: Type](mirror: Expr[Mirror.Of[A]]): MaterializedMirror =
      create(mirror).fold(memberName => abort(Failure.MirrorMaterialization(TypeRepr.of[A], memberName)), identity)

    private def create(mirror: Expr[Mirror]): Either[String, MaterializedMirror] = {
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
  }

  private def tupleTypeElements(tp: TypeRepr): List[TypeRepr] = {
    @tailrec def loop(tp: TypeRepr, acc: List[TypeRepr]): List[TypeRepr] = tp match {
      case AppliedType(pairTpe, List(hd: TypeRepr, tl: TypeRepr)) => loop(tl, hd :: acc)
      case _                                                      => acc
    }
    loop(tp, Nil).reverse
  }

  private def low(tp: TypeRepr): TypeRepr = tp match {
    case tp: TypeBounds => tp.low
    case tp             => tp
  }

  private def findMemberType(tp: TypeRepr, name: String): Either[String, TypeRepr] =
    tp match {
      case Refinement(_, `name`, tp) => Right(low(tp))
      case Refinement(parent, _, _)  => findMemberType(parent, name)
      case AndType(left, right)      => findMemberType(left, name).orElse(findMemberType(right, name))
      case _                         => Left(name)
    }
}
